package com.heima.article.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {
    // 单页最大加载的数字
    private final static short MAX_PAGE_SIZE = 50;

    @Autowired
    private ApArticleMapper apArticleMapper;
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
}
