package com.heima.article.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.search.vos.SearchArticleVo;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import freemarker.template.Configuration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@Transactional
public class ArticleFreemarkerServiceImpl implements ArticleFreemarkerService {

    @Autowired
    private Configuration configuration;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ApArticleService apArticleService;
    /**
     * 构建文章并上传到MinIO存储服务
     * @param apArticle 文章对象，包含文章的基本信息
     * @param content 文章内容，以JSON字符串格式存储
     */
    @Async
    @Override
    public void buildArticleToMinIO(ApArticle apArticle, String content) {
        // 检查文章内容是否不为空
        if(StringUtils.isNotBlank( content)){
            Template template = null;
            StringWriter out=new StringWriter();
            try {
                // 获取文章模板并处理内容数据
                // 获取文章模板文件
                template=configuration.getTemplate("article.ftl");
                // 创建数据模型并填充内容数据
                Map<String,Object> contentDataModel=new HashMap<>();
                contentDataModel.put("content", JSONArray.parseArray(content));
                // 使用模板和数据模型生成最终输出
                template.process(contentDataModel,out);

            }catch (Exception e){
                e.printStackTrace();
            }
            // 将处理后的内容转换为输入流并上传到文件存储服务
            InputStream in = new ByteArrayInputStream(out.toString().getBytes());
            String path=fileStorageService.uploadHtmlFile("",apArticle.getId()+".html",in);
            // 更新文章的静态URL路径
            apArticleService.update(Wrappers.<ApArticle>lambdaUpdate().eq(ApArticle::getId,apArticle.getId())
                    .set(ApArticle::getStaticUrl,path));
            //发送消息，创建索引
            createArticleESIndex(apArticle,content,path);
        }
    }
    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;
    /**
     * 创建文章ES索引
     * 该方法用于构建文章搜索所需的VO对象，并将其发送到Kafka队列中进行异步索引创建
     * @param apArticle 文章实体对象，包含文章的基本信息
     * @param content 文章内容文本
     * @param path 文章静态页面路径
     */
    private void createArticleESIndex(ApArticle apArticle, String content, String path) {
        // 构造搜索文章VO对象
        SearchArticleVo searchArticleVo = new SearchArticleVo();
        // 复制文章基础属性到搜索VO对象
        BeanUtils.copyProperties(apArticle,searchArticleVo);
        // 设置文章内容和静态URL路径
        searchArticleVo.setContent(content);
        searchArticleVo.setStaticUrl(path);
        // 发送消息到Kafka，用于ES索引同步
        kafkaTemplate.send(ArticleConstants.ARTICLE_ES_SYNC_TOPIC, JSONArray.toJSONString(searchArticleVo));
    }


}
