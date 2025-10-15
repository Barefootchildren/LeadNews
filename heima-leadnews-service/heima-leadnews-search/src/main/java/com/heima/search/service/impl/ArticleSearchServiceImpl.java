package com.heima.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.UserSearchDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.service.ApUserSearchService;
import com.heima.search.service.ArticleSearchService;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ArticleSearchServiceImpl implements ArticleSearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    @Autowired
    private ApUserSearchService apUserSearchService;

    /**
     * ES文章分页检索接口实现
     * <p>
     * 根据用户搜索关键词和时间范围，在Elasticsearch中进行文章内容的分页查询，
     * 支持按标题和内容匹配，并高亮显示匹配结果。
     *
     * @param dto 用户搜索请求参数对象，包含搜索词、最小行为时间、分页大小等信息
     * @return 响应结果，包含查询到的文章列表及高亮标题
     * @throws IOException 当与ES通信异常时抛出
     */
    @Override
    public ResponseResult search(UserSearchDto dto) throws IOException {
        // 参数校验：检查dto是否为空或搜索关键词是否为空
        if(dto==null||StringUtils.isBlank(dto.getSearchWords())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        ApUser user = AppThreadLocalUtil.getUser();

        if (user!=null&&dto.getFromIndex()==0){
            apUserSearchService.insert(dto.getSearchWords(),user.getId());
        }

        // 构建ES搜索请求对象，指定索引名称
        SearchRequest searchRequest = new SearchRequest("app_info_article");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        // 构造组合查询条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // 构造字符串查询，支持在title和content字段中模糊匹配关键词
        QueryStringQueryBuilder queryStringQueryBuilder = QueryBuilders.queryStringQuery(dto.getSearchWords())
                .field("title").field("content").defaultOperator(Operator.OR);

        // 添加文本查询条件到bool查询中
        boolQueryBuilder.must(queryStringQueryBuilder);

        // 构造时间范围过滤器，筛选发布时间小于最小行为时间的数据
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("publishTime").lt(dto.getMinBehotTime().getTime());
        boolQueryBuilder.filter(rangeQueryBuilder);

        // 设置分页参数
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(dto.getPageSize());

        // 按发布时间倒序排序
        searchSourceBuilder.sort("publishTime", SortOrder.DESC);

        // 配置高亮显示设置
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title"); // 对标题字段进行高亮处理
        highlightBuilder.preTags("<font style='color: red; font-size: inherit;'>"); // 高亮前缀标签
        highlightBuilder.postTags("</font>"); // 高亮后缀标签
        searchSourceBuilder.highlighter(highlightBuilder);

        // 将查询条件加入source builder
        searchSourceBuilder.query(boolQueryBuilder);

        // 绑定搜索源到请求对象
        searchRequest.source(searchSourceBuilder);

        // 执行ES搜索操作
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        // 处理搜索响应结果
        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            // 获取原始文档JSON并转换为Map结构
            String json = hit.getSourceAsString();
            Map map = JSON.parseObject(json, Map.class);

            // 判断是否存在高亮字段，若有则替换原title为高亮版本
            if (hit.getHighlightFields()!=null && hit.getHighlightFields().size()>0){
                Text[] titles = hit.getHighlightFields().get("title").getFragments();
                String title = StringUtils.join(titles);
                map.put("h_title",title); // 存入高亮标题字段
            }else {
                map.put("h_title",map.get("title")); // 若无高亮则保留原标题
            }

            list.add(map);
        }

        // 返回封装好的响应结果
        return ResponseResult.okResult(list);
    }

}