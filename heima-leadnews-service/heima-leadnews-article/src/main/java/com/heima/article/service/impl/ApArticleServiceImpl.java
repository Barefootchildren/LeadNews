package com.heima.article.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.common.constants.ArticleConstants;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.nntp.Article;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
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

}
