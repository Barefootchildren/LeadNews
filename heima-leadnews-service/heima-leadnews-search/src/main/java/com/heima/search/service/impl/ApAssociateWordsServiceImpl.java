package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.UserSearchDto;
import com.heima.model.search.pojos.ApAssociateWords;
import com.heima.search.service.ApAssociateWordsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Description:
 * @Version: V1.0
 */
@Service
public class ApAssociateWordsServiceImpl implements ApAssociateWordsService {

    @Autowired
    MongoTemplate mongoTemplate;

    /**
     * 联想词
     * 根据用户输入的搜索关键词，查询匹配的联想词列表
     * @param userSearchDto 用户搜索参数对象，包含搜索词和分页信息
     * @return 返回匹配的联想词列表，最多返回20条记录
     */
    @Override
    public ResponseResult findAssociate(UserSearchDto userSearchDto) {
        // 参数校验：检查搜索参数是否为空或搜索词是否为空
        if(userSearchDto==null||StringUtils.isBlank(userSearchDto.getSearchWords())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }

        // 限制每页最大返回数量为20条
        if(userSearchDto.getPageSize()>20){
            userSearchDto.setPageSize(20);
        }

        // 构造MongoDB查询条件，使用正则表达式模糊匹配联想词
        Query query = Query.query(Criteria.where("associateWords").regex(".*?\\" + userSearchDto.getSearchWords() + ".*"));
        query.limit(userSearchDto.getPageSize());
        List<ApAssociateWords> wordsList=mongoTemplate.find(query,ApAssociateWords.class);
        return ResponseResult.okResult(wordsList);
    }

}