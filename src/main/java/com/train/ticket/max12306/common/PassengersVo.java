package com.train.ticket.max12306.common;

import lombok.Data;

/**
 * @ClassName PassengersVo 编辑乘车人
 * @ClassExplain: 说明
 * @Author Duxiaoyu
 * @Date 2020/8/23 10:55
 * @Since V 1.0
 */
@Data
public class PassengersVo {


    private String isUserSelf;

    /**
     * 加密字符串
     */
    private String allEncStr;

    /**
     * 证件号码
     */
    private String passengerIdNo;

    /**
     * 证件类型编号
     */
    private String passengerIdTypeCode;

    /**
     * 乘车人姓名
     */
    private String passengerName;
}
