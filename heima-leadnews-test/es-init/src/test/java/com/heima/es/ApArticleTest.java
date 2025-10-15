package com.heima.es;

import com.alibaba.fastjson.JSON;
import com.heima.es.mapper.ApArticleMapper;
import com.heima.es.pojo.SearchArticleVo;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ApArticleTest {
    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private RestHighLevelClient restHighLevelClient;
        /**
     * 初始化文章数据到Elasticsearch
     * 注意：数据量的导入，如果数据量过大，需要分页导入
     * @throws Exception 当ES操作失败时抛出异常
     */
    @Test
    public void init() throws Exception {
        // 查询所有文章数据
        List<SearchArticleVo> articleVos = apArticleMapper.loadArticleList();

        // 创建批量请求对象，指定索引名称
        BulkRequest bulkRequest = new BulkRequest("app_info_article");

        // 遍历文章列表，构建索引请求并添加到批量请求中
        for (SearchArticleVo articleVo : articleVos){
            IndexRequest indexRequest = new IndexRequest().id(articleVo.getId().toString()).source(JSON.toJSONString(articleVo), XContentType.JSON);
            bulkRequest.add(indexRequest);
        }

        // 执行批量导入操作
        restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
    }


}