package com.springleaf.couponfury.merchant.admin.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
* 分页参数请求DTO
*/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageParamReqDTO {

    /**
     * 当前页码
     */
    @Schema(description = "当前页码")
    private Integer pageNum;
    /**
     * 每页显示条数
     */
    @Schema(description = "每页显示条数")
    private Integer pageSize;
}
