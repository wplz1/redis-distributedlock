package com.weiwei.zll.bootredis.entity;

import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class User {

    private int id;

    private String name;

    private Date birthday;

    private List<String> interesting;

    private Map<String, Object> others;

}
