package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import org.springframework.beans.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.service.WmNewsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {
        /**
     * 查询所有自媒体文章列表
     * @param dto 查询条件封装对象，包含分页信息、状态、频道ID、发布时间范围、关键字等查询条件
     * @return 返回分页查询结果，包含文章列表数据和分页信息
     */
    @Override
    public ResponseResult findAll(WmNewsPageReqDto dto) {
        // 参数校验
        if(dto==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        dto.checkParam();

        // 用户身份校验
        WmUser user = WmThreadLocalUtil.getUser();
        if(user==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }

        // 构造分页对象
        Page page = new Page(dto.getPage(), dto.getSize());

        // 创建Lambda查询构造器用于构建查询条件
        LambdaQueryWrapper<WmNews> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        // 根据状态条件进行等值查询
        if(dto.getStatus()!=null){
            lambdaQueryWrapper.eq(WmNews::getStatus,dto.getStatus());
        }

        // 根据频道ID条件进行等值查询
        if(dto.getChannelId()!=null){
            lambdaQueryWrapper.eq(WmNews::getChannelId,dto.getChannelId());
        }

        // 根据发布时间范围进行区间查询
        if(dto.getBeginPubDate()!=null&&dto.getEndPubDate()!=null){
            lambdaQueryWrapper.between(WmNews::getPublishTime,dto.getBeginPubDate(),dto.getEndPubDate());
        }

        // 根据关键字进行模糊查询
        if(StringUtils.isNotBlank(dto.getKeyword())){
            lambdaQueryWrapper.like(WmNews::getTitle,dto.getKeyword());
        }

        // 限定当前用户的文章
        lambdaQueryWrapper.eq(WmNews::getUserId,user.getId());
        // 按创建时间倒序排列
        lambdaQueryWrapper.orderByDesc(WmNews::getCreatedTime);

        // 执行分页查询
        page=page(page,lambdaQueryWrapper);

        // 封装返回结果
        PageResponseResult pageResponseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        pageResponseResult.setData(page.getRecords());
        return pageResponseResult;
    }
    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService;
    /**
     * 提交新闻内容
     * @param dto 新闻数据传输对象，包含新闻的标题、内容、图片等信息
     * @return ResponseResult 响应结果，成功时返回SUCCESS，参数无效时返回PARAM_INVALID
     */
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        // 参数校验，如果dto或其内容为空则返回参数无效错误
        if(dto==null||dto.getContent()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        // 将DTO对象转换为实体对象
        WmNews wmNews = new WmNews();
        BeanUtils.copyProperties(dto,wmNews);

        // 处理图片信息，将图片列表转换为逗号分隔的字符串
        if(dto.getImages()!=null&&dto.getImages().size()>0){
            String imageStr = StringUtils.join(dto.getImages(), ",");
            wmNews.setImages(imageStr);
        }

        // 如果新闻类型为自动，则设置为null
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }

        // 保存或更新新闻信息
        saveOrUpdateWmNews(wmNews);

        // 如果只是存为草稿，则直接返回成功
        if(dto.getStatus().equals(WmNews.Status.NORMAL.getCode())){
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }

        // 提取新闻内容中的素材URL信息
        List<String> materials = extractUrlInfo(dto.getContent());

        // 保存内容相关的素材信息
        saveRelativeInfoForContent(materials,wmNews.getId());

        // 保存封面相关的素材信息
        saveRelativeInfoForCover(dto,wmNews,materials);
        wmNewsAutoScanService.autoScanWmNews(wmNews.getId());

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;
    /**
     * 保存或更新自媒体文章
     * @param wmNews 自媒体文章对象
     */
    private void saveOrUpdateWmNews(WmNews wmNews) {
        // 设置文章基础信息
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short)1);

        // 根据文章ID判断是新增还是更新操作
        if(wmNews.getId()==null){
            save(wmNews);
        }else {
            // 更新操作时，先删除关联的素材关系，再更新文章信息
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId,wmNews.getId()));
            updateById(wmNews);
        }
    }

    /**
     * 从JSON内容中提取图片URL信息
     * @param content 包含图片信息的JSON字符串，格式为包含type和value字段的Map数组
     * @return 包含所有图片URL的字符串列表
     */
    private List<String> extractUrlInfo(String content){
        List<String> materials = new ArrayList<>();
        // 解析JSON数组内容
        List<Map> maps = JSON.parseArray(content, Map.class);
        // 遍历解析后的Map集合，筛选出类型为image的条目并提取URL
        for(Map map:maps){
            if(map.get("type").equals("image")){
                String imgUrl=(String) map.get("value");
                materials.add(imgUrl);
            }
        }
        return materials;
    }

    private void saveRelativeInfoForContent(List<String> materials,Integer newsId){
        saveRelativeInfo(materials,newsId,WemediaConstants.WM_CONTENT_REFERENCE);
    }
    @Autowired
    private WmMaterialMapper wmMaterialMapper;
    /**
     * 保存文章或素材关联信息
     * @param materials 素材URL列表
     * @param newsId 新闻ID
     * @param type 关联类型（0-图片素材关联 1-文章封面关联）
     */
    private void saveRelativeInfo(List<String> materials,Integer newsId,Short type){
        // 验证素材列表不为空
        if(materials!=null&&!materials.isEmpty()){
            // 查询数据库中对应的素材信息
            List<WmMaterial> dbMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));
            // 验证素材是否存在
            if(dbMaterials==null||dbMaterials.size()==0){
                throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
            }
            // 验证素材数量是否匹配
            if (materials.size()!=dbMaterials.size()){
                throw new CustomException(AppHttpCodeEnum.MATERIASL_REFERENCE_FAIL);
            }
            // 提取素材ID列表并保存关联关系
            List<Integer> idList = dbMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());
            wmNewsMaterialMapper.saveRelations(idList,newsId,type);
        }
    }

    /**
     * 保存封面图片关联信息
     * @param dto 新闻DTO对象，包含新闻的基本信息和图片列表
     * @param wmNews 新闻实体对象，用于设置新闻类型和图片信息
     * @param materials 素材列表，用于自动匹配封面图片
     */
    private void saveRelativeInfoForCover(WmNewsDto dto,WmNews wmNews,List<String> materials){
        List<String> images = dto.getImages();
        // 自动匹配封面图片逻辑
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            // 根据素材数量设置新闻类型和封面图片
            if(materials.size()>=3){
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images=materials.stream().limit(3).collect(Collectors.toList());
            } else if (materials.size()>=1&&materials.size()<3) {
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images=materials.stream().limit(1).collect(Collectors.toList());
            }else {
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }
            // 更新新闻的图片信息
            if (images!=null&&images.size()>0){
                wmNews.setImages(StringUtils.join(images,","));
            }
            updateById(wmNews);
        }
        // 保存图片与新闻的关联关系
        if(images!=null&&images.size()>0){
            saveRelativeInfo(images,wmNews.getId(),WemediaConstants.WM_COVER_REFERENCE);
        }
    }

}
