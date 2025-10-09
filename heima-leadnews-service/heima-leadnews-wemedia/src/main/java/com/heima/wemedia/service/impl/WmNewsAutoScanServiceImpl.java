package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.apis.article.IArticleClient;
import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.common.tess4j.Tess4jClient;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmSensitive;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.common.SensitiveWordUtil;
import com.heima.wemedia.mapper.WmChannelMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmSensitiveMapper;
import com.heima.wemedia.mapper.WmUserMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class WmNewsAutoScanServiceImpl implements WmNewsAutoScanService {
    @Autowired
    private WmNewsMapper wmNewsMapper;
    @Resource
    private IArticleClient articleClient;

    @Autowired
    private WmChannelMapper wmChannelMapper;

    @Autowired
    private WmUserMapper wmUserMapper;
    @Autowired
    private GreenTextScan greenTextScan;
    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private GreenImageScan greenImageScan;
    @Autowired
    private Tess4jClient tess4jClient;

    /**
     * 自动扫描审核自媒体文章
     * @param id 文章ID
     */
    @Override
    @Async
    public void autoScanWmNews(Integer id) {
        // 查询文章信息
        WmNews wmNews = wmNewsMapper.selectById(id);
        if(wmNews==null){
            throw new RuntimeException("WmNewsAutoScanServiceImpl-文章不存在");
        }

        // 只对已提交状态的文章进行审核
        if(wmNews.getStatus().equals(WmNews.Status.SUBMIT.getCode())){
            // 提取文章正文和图片
            Map<String,Object> textAndImages= handleTextAndImages(wmNews);

            // 执行敏感词扫描处理，检测内容是否包含敏感信息
            boolean isSensitive=handleSensitiveScan((String)textAndImages.get("content"),wmNews);
            // 如果内容不包含敏感词，则直接返回，不再进行后续处理
            if (!isSensitive)return;


            // 文本内容审核
            boolean isTextScan= handleTextScan((String)textAndImages.get("content"),wmNews);
            if(!isTextScan){
                return;
            }

            // 图片审核
            boolean isImagesScan= handleImagesScan((List<String>)textAndImages.get("images"),wmNews);
            if(!isImagesScan){
                return;
            }

            // 保存到APP端并更新文章状态
            ResponseResult responseResult=saveAppArticle(wmNews);
            if(!responseResult.getCode().equals(200)){
                throw new RuntimeException("WmNewsAutoScanServiceImpl-文章审核，保存app端文章失败");
            }

            wmNews.setArticleId((Long) responseResult.getData());
            updateWmNews(wmNews,(short)9,"审核通过");
        }
    }
    @Autowired
    private WmSensitiveMapper wmSensitiveMapper;
    /**
     * 处理敏感词扫描
     * @param content 待检测的文本内容
     * @param wmNews 新闻对象，用于更新审核状态
     * @return 检测结果，true表示无敏感词，false表示存在敏感词
     */
    private boolean handleSensitiveScan(String content, WmNews wmNews) {
        boolean flag=true;
        // 查询所有敏感词
        List<WmSensitive> wmSensitives = wmSensitiveMapper.selectList(Wrappers
                .<WmSensitive>lambdaQuery().select(WmSensitive::getSensitives));
        // 提取敏感词列表
        List<String> sensitiveList = wmSensitives.stream()
                .map(WmSensitive::getSensitives).collect(Collectors.toList());
        // 初始化敏感词检测工具
        SensitiveWordUtil.initMap(sensitiveList);
        // 匹配文本中的敏感词
        Map<String, Integer> map = SensitiveWordUtil.matchWords(content);
        // 如果发现敏感词，更新新闻状态为审核不通过
        if(map.size()>0){
            updateWmNews(wmNews,(short) 2,"当前文章有违规内容"+map);
            flag=false;
        }
        return flag;
    }



    /**
     * 处理新闻内容中的文本和图片信息
     * @param wmNews 新闻对象，包含内容和图片信息
     * @return 包含处理后文本内容和图片列表的Map，key为"content"和"images"
     */
    private Map<String, Object> handleTextAndImages(WmNews wmNews) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> images=new ArrayList<>();
        // 解析新闻内容，提取文本和图片
        if(StringUtils.isNotBlank(wmNews.getContent())){
            List<Map> maps = JSON.parseArray(wmNews.getContent(), Map.class);
            for (Map map : maps) {
                if(map.get("type").equals("text")){
                    stringBuilder.append(map.get("value"));
                }
                if(map.get("type").equals("image")){
                    images.add((String) map.get("value"));
                }
            }
        }
        // 合并新闻对象中的封面图片信息
        if(StringUtils.isNotBlank(wmNews.getImages())){
            String[] split = wmNews.getImages().split(",");
            images.addAll(Arrays.asList(split));
        }
        // 构造返回结果
        Map<String,Object> resultMap=new HashMap<>();
        resultMap.put("content",stringBuilder.toString());
        resultMap.put("images",images);
        return resultMap;
    }


    /**
     * 处理文本扫描审核
     * @param content 文章内容
     * @param wmNews 文章对象
     * @return 审核是否通过，true表示通过，false表示不通过
     */
    private boolean handleTextScan(String content, WmNews wmNews) {
        boolean flag=true;
        // 如果标题和内容拼接后的长度为1，直接返回true
        if((wmNews.getTitle()+"-"+ content).length()==1){
            return flag;
        }
        try {
            // 调用阿里云文本审核接口进行内容审核
            Map map = greenTextScan.greeTextScan((wmNews.getTitle() + "-" + content));
            if (map != null){
                // 如果审核建议为"block"，表示内容违规，更新文章状态为审核失败
                if(map.get("suggestion").equals("block")){
                    flag=false;
                    updateWmNews(wmNews,(short) 2,"当前文章中存在违规内容");
                }
                // 如果审核建议为"review"，表示内容需要人工审核，更新文章状态为待审核
                if(map.get("suggestion").equals("review")){
                    flag=false;
                    updateWmNews(wmNews,(short) 3,"当前文章中存在待审核内容");
                }
            }
        }catch (Exception e){
            // 发生异常时，审核不通过
            flag=false;
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 更新自媒体新闻状态和原因
     * @param wmNews 自媒体新闻对象
     * @param status 新闻状态
     * @param reason 更新原因
     */
    private void updateWmNews(WmNews wmNews, short status, String reason) {
        // 更新新闻状态和原因信息
        wmNews.setStatus(status);
        wmNews.setReason(reason);
        wmNewsMapper.updateById(wmNews);
    }

    /**
     * 处理图片扫描审核功能
     * @param images 待扫描的图片URL列表
     * @param wmNews 新闻对象，用于更新审核状态
     * @return boolean 扫描结果标识，true表示通过审核，false表示未通过审核
     */
    private boolean handleImagesScan(List<String> images, WmNews wmNews) {
        boolean flag=true;
        // 检查图片列表是否为空，如果为空则直接返回true
        if(images==null || images.size()==0){
            return flag;
        }
        // 去除重复的图片URL
        images = images.stream().distinct().collect(Collectors.toList());
        List<byte[]> imageList=new ArrayList<>();
        // 下载所有图片文件并转换为字节数组
        for (String image : images) {
            byte[] bytes = fileStorageService.downLoadFile(image);
            try {
                // 创建字节数组输入流，用于读取图片字节数据
                ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                // 使用ImageIO读取图片数据，转换为BufferedImage对象
                BufferedImage imageFile = ImageIO.read(in);
                // 调用OCR客户端识别图片中的文字内容
                String result = tess4jClient.doOCR(imageFile);
                // 处理敏感词扫描，检查识别结果中是否包含敏感内容
                boolean isSensitive = handleSensitiveScan(result, wmNews);
                // 如果不包含敏感内容，则直接返回检测结果
                if(!isSensitive){
                    return isSensitive;
                }

            }catch (Exception e){
                e.printStackTrace();
            }
            imageList.add(bytes);
        }
        try {
            // 调用阿里云图片审核服务进行内容安全检测
            Map map = greenImageScan.imageScan(imageList);
            if (map != null){
                // 根据审核建议更新新闻状态
                if(map.get("suggestion").equals("block")){
                    flag=false;
                    updateWmNews(wmNews,(short) 2,"当前图片中存在违规内容");
                }
                if(map.get("suggestion").equals("review")){
                    flag=false;
                    updateWmNews(wmNews,(short) 3,"当前图片中存在待审核内容");
                }
            }
        }catch (Exception e){
            // 发生异常时设置flag为false并打印异常堆栈
            flag=false;
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 保存应用文章
     * @param wmNews 新闻对象，包含文章的基本信息
     * @return ResponseResult 保存结果响应
     */
    private ResponseResult saveAppArticle(WmNews wmNews) {
        // 构造文章DTO对象并复制基础属性
        ArticleDto dto = new ArticleDto();
        BeanUtils.copyProperties(wmNews,dto);
        dto.setLayout(wmNews.getType());

        // 设置频道名称
        WmChannel wmChannel = wmChannelMapper.selectById(wmNews.getChannelId());
        if (wmChannel != null){
            dto.setChannelName(wmChannel.getName());
        }

        // 设置作者ID和作者名称
        dto.setAuthorId(wmNews.getUserId().longValue());
        WmUser wmUser = wmUserMapper.selectById(wmNews.getUserId());
        if (wmUser != null){
            dto.setAuthorName(wmUser.getName());
        }

        // 如果存在文章ID则设置到DTO中
        if(wmNews.getArticleId()!=null){
            dto.setId(wmNews.getArticleId());
        }

        // 设置创建时间并调用文章客户端保存文章
        dto.setCreatedTime(new Date());
        ResponseResult responseResult = articleClient.saveArticle(dto);
        return responseResult;
    }

}
