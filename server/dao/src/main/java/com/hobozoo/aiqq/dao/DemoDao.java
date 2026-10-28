package com.hobozoo.aiqq.dao;

import com.hobozoo.aiqq.entity.demo.Cat;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;

public class DemoDao {

    @Autowired
    public SqlSessionFactory sqlSessionFactory;

    public HashMap<String,String> test(){

        HashMap<String, String> hash = new HashMap<>();

        Cat cat = new Cat();
        cat.setName("hbz");

        sqlSessionFactory.openSession().select(
                "com.hobozoo.aiqq.dao.mapper.DemoMapper.selectCatByCat",
                cat,
                resultContext -> {
                    Cat result = (Cat) resultContext.getResultObject();
                    hash.put(result.getId(),result.getName());
                });

        return hash;

    }




}
