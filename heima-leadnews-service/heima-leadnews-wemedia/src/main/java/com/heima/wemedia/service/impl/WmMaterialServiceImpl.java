package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.file.service.FileStorageService;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.service.WmMaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.Date;
import java.util.UUID;
@Slf4j
@Service
@Transactional
public class WmMaterialServiceImpl extends ServiceImpl<WmMaterialMapper, WmMaterial> implements WmMaterialService {
    @Resource
    private FileStorageService fileStorageService;
    /**
     * 上传图片文件并保存素材信息
     *
     * @param multipartFile 上传的文件对象，不能为空且大小不能为0
     * @return ResponseResult 返回上传结果，成功时返回素材信息，失败时返回错误码
     */
    @Override
    public ResponseResult uploadPicture(MultipartFile multipartFile) {

        if(multipartFile == null||multipartFile.getSize()==0){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        // 生成唯一文件名并获取文件后缀
        String fileName = UUID.randomUUID().toString().replace("-", "");
        String originalFilename = multipartFile.getOriginalFilename();
        String postfix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileId=null;
        try {
            // 上传文件到存储服务
            fileId=fileStorageService.uploadImgFile("",fileName+postfix,multipartFile.getInputStream());
            log.info("上传图片到MinIO中，fileId:{}",fileId);
        }catch (Exception e){
            e.printStackTrace();
            log.error("WmMaterialServiceImpl-上传文件失败");
        }
        // 创建并保存素材记录
        WmMaterial wmMaterial = new WmMaterial();
        wmMaterial.setUserId(WmThreadLocalUtil.getUser().getId());
        wmMaterial.setUrl(fileId);
        wmMaterial.setIsCollection((short)0);
        wmMaterial.setType((short)0);
        wmMaterial.setCreatedTime(new Date());
        save(wmMaterial);
        return ResponseResult.okResult(wmMaterial);
    }

    /**
     * 查询素材列表
     * @param dto 素材查询条件对象，包含分页信息、收藏状态等查询参数
     * @return 返回分页查询结果，包含素材列表数据和分页信息
     */
    @Override
    public ResponseResult findList(WmMaterialDto dto) {
        // 参数校验
        dto.checkParam();

        // 构造分页对象
        IPage page=new Page(dto.getPage(),dto.getSize());

        // 构造查询条件
        LambdaQueryWrapper<WmMaterial> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if(dto.getIsCollection()!=null&&dto.getIsCollection()==1){
            lambdaQueryWrapper.eq(WmMaterial::getIsCollection,dto.getIsCollection());
        }
        lambdaQueryWrapper.eq(WmMaterial::getUserId,WmThreadLocalUtil.getUser().getId());
        lambdaQueryWrapper.orderByDesc(WmMaterial::getCreatedTime);

        // 执行分页查询
        page=page(page,lambdaQueryWrapper);

        // 封装返回结果
        PageResponseResult pageResponseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        pageResponseResult.setData(page.getRecords());
        return pageResponseResult;
    }

}
