package com.heima.article.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
        }
    }

}
