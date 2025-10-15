package com.heima.search.listener;

import com.alibaba.fastjson.JSON;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.search.vos.SearchArticleVo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SyncArticleListener {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    /**
     * 监听Kafka消息队列，同步文章数据到Elasticsearch
     * @param message 从Kafka接收到的消息内容，应为JSON格式的文章数据
     */
    @KafkaListener(topics = ArticleConstants.ARTICLE_ES_SYNC_TOPIC)
    public void onMessage(String message){
        // 检查消息是否为空
        if (StringUtils.isNotBlank(message)){
            log.info("syncArticleListener,message={}",message);
            // 解析消息为SearchArticleVo对象
            SearchArticleVo searchArticleVo = JSON.parseObject(message, SearchArticleVo.class);
            // 构建Elasticsearch索引请求
            IndexRequest indexRequest = new IndexRequest("app_info_article");
            indexRequest.id(searchArticleVo.getId().toString());
            indexRequest.source(message,XContentType.JSON);
            try{
                // 执行索引操作，将文章数据同步到Elasticsearch
                restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
            }catch (Exception e){
                e.printStackTrace();
                log.error("sync es error={}",e);
            }
        }
    }

}