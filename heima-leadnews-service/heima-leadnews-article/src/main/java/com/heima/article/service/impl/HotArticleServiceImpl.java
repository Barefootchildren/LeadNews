package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.apis.wemedia.IWemediaClient;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.HotArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.vos.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.pojos.WmChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class HotArticleServiceImpl implements HotArticleService {
    @Autowired
    private ApArticleMapper apArticleMapper;
    /**
     * 计算热门文章并缓存到Redis中
     * 该方法会获取最近5天的文章数据，计算热度后将结果存储到Redis缓存中
     */
    @Override
    public void computeHotArticle() {
        // 计算5天前的日期作为查询起始时间
        LocalDate localDate = LocalDate.of(2020, 9, 10).minusDays(5);

        // 查询最近5天的文章列表
        List<ApArticle> apArticleList = apArticleMapper.findArticleListByLast5days(Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant()));

        // 计算文章热度值
        List<HotArticleVo> hotArticleVoList = null;
        try {
            hotArticleVoList = computeHotArticle(apArticleList);
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // 将热门文章缓存到Redis中
        cacheTagToRedis(hotArticleVoList);
    }

    @Autowired
    private IWemediaClient wemediaClient;

    @Autowired
    private CacheService cacheService;
    /**
     * 将热门文章列表按照频道分类缓存到Redis中
     * @param hotArticleVoList 热门文章列表
     */
    private void cacheTagToRedis(List<HotArticleVo> hotArticleVoList) {
        // 获取所有频道信息
        ResponseResult responseResult = wemediaClient.getChannels();
        if(responseResult.getCode().equals(200)){
            String channelJson = JSON.toJSONString(responseResult.getData());
            List<WmChannel> wmChannels = JSON.parseArray(channelJson, WmChannel.class);
            // 遍历所有频道，将对应频道的文章进行排序并缓存
            if(wmChannels!=null&&wmChannels.size()>0){
                for(WmChannel wmChannel:wmChannels){
                    List<HotArticleVo> hotArticleVos = hotArticleVoList.stream().filter(x -> x.getChannelId().equals(wmChannel.getId())).collect(Collectors.toList());
                    sortAndCache(hotArticleVos,ArticleConstants.HOT_ARTICLE_FIRST_PAGE+wmChannel.getId());
                }
            }
        }
        // 缓存默认标签的热门文章
        sortAndCache(hotArticleVoList,ArticleConstants.HOT_ARTICLE_FIRST_PAGE+ArticleConstants.DEFAULT_TAG);
    }


        /**
     * 对热门文章列表进行排序并缓存
     * @param hotArticleVos 热门文章列表
     * @param key 缓存键值
     */
    private void sortAndCache(List<HotArticleVo> hotArticleVos, String key) {
        // 按照文章评分降序排序
        hotArticleVos=hotArticleVos.stream().sorted(Comparator
                .comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
        // 如果文章数量超过30条，只保留前30条
        if (hotArticleVos.size()>30){
            hotArticleVos=hotArticleVos.subList(0,30);
        }
        // 将排序后的文章列表缓存到redis中
        cacheService.set(key,JSON.toJSONString(hotArticleVos));
    }


    private List<HotArticleVo> computeHotArticle(List<ApArticle> apArticleList) throws InvocationTargetException, IllegalAccessException {
        ArrayList<HotArticleVo> hotArticleVoList = new ArrayList<>();
        if (apArticleList != null&&apArticleList.size()>0){
            for (ApArticle apArticle : apArticleList) {
                HotArticleVo hot = new HotArticleVo();
                BeanUtils.copyProperties(apArticle,hot);
                Integer score=computeScore(apArticle);
                hot.setScore(score);
                hotArticleVoList.add(hot);
            }
        }
        return hotArticleVoList;
    }

    /**
     * 计算文章热度得分
     * 根据文章的点赞数、收藏数、评论数和浏览量计算综合热度得分
     * @param apArticle 文章对象，包含各种互动数据
     * @return 计算得出的文章热度得分
     */
    private Integer computeScore(ApArticle apArticle) {
        Integer score = 0;
        // 计算点赞数得分
        if(apArticle.getLikes()!=null){
            score+=apArticle.getLikes()* ArticleConstants.HOT_ARTICLE_LIKE_WEIGHT;
        }
        // 计算收藏数得分
        if (apArticle.getCollection()!=null){
            score+=apArticle.getCollection()* ArticleConstants.HOT_ARTICLE_COLLECTION_WEIGHT;
        }
        // 计算评论数得分
        if(apArticle.getComment()!=null){
            score+=apArticle.getComment()* ArticleConstants.HOT_ARTICLE_COMMENT_WEIGHT;
        }
        // 计算浏览量得分
        if (apArticle.getViews()!=null){
            score+=apArticle.getViews();
        }
        return score;
    }

}
