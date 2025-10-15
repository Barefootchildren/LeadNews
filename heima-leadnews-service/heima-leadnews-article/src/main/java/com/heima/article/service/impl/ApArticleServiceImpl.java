package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.article.vos.HotArticleVo;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.mess.ArticleVisitStreamMess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.nntp.Article;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {
    // 单页最大加载的数字
    private final static short MAX_PAGE_SIZE = 50;

    @Autowired
    private ApArticleMapper apArticleMapper;
    @Autowired
    private CacheService cacheService;

    /**
     * 加载文章列表数据
     * @param dto 文章查询参数对象，包含分页大小、标签、时间范围等查询条件
     * @param loadtype 加载类型，决定数据加载方式（加载更多或加载最新）
     * @return ResponseResult 包含文章列表的响应结果
     */
    @Override
    public ResponseResult load(Short loadtype, ArticleHomeDto dto) {
                // 设置分页大小，如果未设置或为0则默认为10，同时限制最大分页大小
                Integer size = dto.getSize();
                if(size == null || size == 0){
                    size = 10;
                }
                size = Math.min(size, MAX_PAGE_SIZE);
                dto.setSize(size);

                // 验证加载类型，如果不是有效的加载类型则设置为默认的加载更多类型
                if (!loadtype.equals(ArticleConstants.LOADTYPE_LOAD_MORE) && !loadtype.equals(ArticleConstants.LOADTYPE_LOAD_NEW)) {
                    loadtype=ArticleConstants.LOADTYPE_LOAD_MORE;
                }

                // 设置查询类型，如果类型为空则默认查询所有类型的文章
                if(StringUtils.isEmpty(dto.getTag())){
                    dto.setTag(ArticleConstants.DEFAULT_TAG);
                }

                // 设置最大行为时间，默认为当前时间
                if(dto.getMaxBehotTime()==null){
                    dto.setMaxBehotTime(new Date());
                }

                // 设置最小行为时间，默认为当前时间
                if (dto.getMinBehotTime()==null){
                    dto.setMinBehotTime(new Date());
                }

                // 执行文章列表查询并返回结果
                List<ApArticle> apArticles = apArticleMapper.loadArticleList(dto, loadtype);
                ResponseResult responseResult = ResponseResult.okResult(apArticles);
                return responseResult;
    }
    @Resource
    ApArticleConfigMapper apArticleConfigMapper;
    @Resource
    ApArticleContentMapper apArticleContentMapper;
    @Resource
    ArticleFreemarkerService articleFreemarkerService;
    /**
     * 保存文章信息
     * @param dto 文章数据传输对象，包含文章的基本信息和内容
     * @return ResponseResult 响应结果，成功时返回文章ID，失败时返回错误信息
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {
        // 参数校验
        if(dto==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        // 拷贝文章基本信息
        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto,apArticle);

        // 根据文章ID是否存在判断是新增还是更新操作
        if(dto.getId()==null){
            // 新增文章
            save(apArticle);
            // 新增文章配置信息
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);
            // 新增文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        }else {
            // 更新文章基本信息
            updateById(apArticle);
            // 更新文章内容
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery()
                    .eq(ApArticleContent::getArticleId, dto.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);
        }
        //异步调用 生成静态文件上传到minio中
         articleFreemarkerService.buildArticleToMinIO(apArticle,dto.getContent());
        return ResponseResult.okResult(apArticle.getId());
    }

    /**
     * 加载文章列表数据
     * @param dto 文章首页数据传输对象，包含标签等查询条件
     * @param type 文章类型标识
     * @param firstPage 是否为首页加载
     * @return 返回文章列表的响应结果
     */
    @Override
    public ResponseResult load2(ArticleHomeDto dto, Short type, boolean firstPage) {
        // 如果是首页加载，优先从缓存中获取热门文章数据
        if(firstPage){
            String jsonStr = cacheService.get(ArticleConstants.HOT_ARTICLE_FIRST_PAGE + dto.getTag());
            if(StringUtils.isNotBlank(jsonStr)){
                List<HotArticleVo> hotArticleVoList = JSON.parseArray(jsonStr, HotArticleVo.class);
                ResponseResult responseResult = ResponseResult.okResult(hotArticleVoList);
                return responseResult;
            }
        }
        // 缓存未命中或非首页加载时，调用load方法获取数据
        return load(type,dto);
    }

    /**
     * 更新文章热度分数
     * @param mess 文章访问流消息对象，包含文章访问相关信息
     */
    @Override
    public void updateScore(ArticleVisitStreamMess mess) {
        // 更新文章信息并获取文章对象
        ApArticle article = updateArticle(mess);

        // 计算文章基础热度分数
        Integer score=computeScore(article);

        // 将基础分数乘以权重系数3作为最终热度分数
        score=score*3;

        // 将文章数据和热度分数存储到Redis中，分别存储到频道页和默认标签页
        replaceDataToRedis(article,score,ArticleConstants.HOT_ARTICLE_FIRST_PAGE+article.getChannelId());
        replaceDataToRedis(article,score,ArticleConstants.HOT_ARTICLE_FIRST_PAGE+ArticleConstants.DEFAULT_TAG);
    }


    /**
     * 更新文章的统计数据
     *
     * @param mess 包含文章访问信息的消息对象，包含文章ID、收藏数、评论数、点赞数和浏览数的增量
     * @return 更新后的文章对象
     */
    private ApArticle updateArticle(ArticleVisitStreamMess mess) {
        // 获取原始文章信息
        ApArticle apArticle = getById(mess.getArticleId());

        // 更新文章的各项统计数据，如果原数据为null则初始化为0，否则累加增量值
        apArticle.setCollection(apArticle.getCollection()==null?0:apArticle.getCollection()+mess.getCollect());
        apArticle.setComment(apArticle.getComment()==null?0:apArticle.getComment()+mess.getComment());
        apArticle.setLikes(apArticle.getLikes()==null?0:apArticle.getLikes()+mess.getLike());
        apArticle.setViews(apArticle.getViews()==null?0:apArticle.getViews()+mess.getView());

        // 保存更新后的文章信息
        updateById(apArticle);
        return apArticle;
    }

    /**
     * 计算文章热度得分
     * @param apArticle 文章对象，包含点赞数、浏览量、评论数、收藏数等信息
     * @return 返回计算后的文章热度得分，基于点赞、浏览、评论、收藏等权重计算得出
     */
    private Integer computeScore(ApArticle apArticle){
        Integer score=0;
        // 根据点赞数和点赞权重计算得分
        if(apArticle.getLikes()!=null){
            score+=apArticle.getLikes()* ArticleConstants.HOT_ARTICLE_LIKE_WEIGHT;
        }
        // 根据浏览量计算得分
        if(apArticle.getViews()!=null){
            score+=apArticle.getViews();
        }
        // 根据评论数和评论权重计算得分
        if(apArticle.getComment()!=null){
            score+=apArticle.getComment()* ArticleConstants.HOT_ARTICLE_COMMENT_WEIGHT;
        }
        // 根据收藏数和收藏权重计算得分
        if (apArticle.getCollection()!=null){
            score+=apArticle.getCollection()* ArticleConstants.HOT_ARTICLE_COLLECTION_WEIGHT;
        }
        return score;
    }
    /**
     * 将文章数据更新到Redis缓存中
     * @param apArticle 需要更新的文章对象
     * @param score 文章的热度分数
     * @param s Redis中存储的键名
     */
    private void replaceDataToRedis(ApArticle apArticle, Integer score, String s) {
        String articleListStr = cacheService.get(s);
        if(StringUtils.isNotBlank(articleListStr)){
            List<HotArticleVo> hotArticleVos = JSON.parseArray(articleListStr, HotArticleVo.class);
            boolean flag=true;

            // 遍历热门文章列表，查找是否已存在该文章
            for(HotArticleVo hotArticleVo:hotArticleVos){
                if(hotArticleVo.getId().equals(apArticle.getId())){
                    hotArticleVo.setScore(score);
                    flag=false;
                    break;
                }
            }

            // 如果文章不存在于热门列表中，需要添加新文章
            if ( flag){
                if(hotArticleVos.size()>=30){
                    // 当热门文章列表已满时，按分数排序并替换最低分文章
                    hotArticleVos=hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
                    HotArticleVo lastHot = hotArticleVos.get(hotArticleVos.size() - 1);
                    if (score > lastHot.getScore()){
                        hotArticleVos.remove(lastHot);
                        HotArticleVo hotArticleVo = new HotArticleVo();
                        BeanUtils.copyProperties(apArticle,hotArticleVo);
                        hotArticleVo.setScore(score);
                        hotArticleVos.add(hotArticleVo);
                    }
                }else {
                    // 当热门文章列表未满时，直接添加新文章
                    HotArticleVo hotArticleVo = new HotArticleVo();
                    BeanUtils.copyProperties(apArticle,hotArticleVo);
                    hotArticleVo.setScore(score);
                    hotArticleVos.add(hotArticleVo);
                }
            }

            // 按分数降序排序并更新Redis缓存
            hotArticleVos=hotArticleVos.stream().sorted(Comparator.comparing(HotArticleVo::getScore).reversed()).collect(Collectors.toList());
            cacheService.set(s,JSON.toJSONString(hotArticleVos));
        }

    }

}
