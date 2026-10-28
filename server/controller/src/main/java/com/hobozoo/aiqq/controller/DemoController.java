package com.hobozoo.aiqq.controller;

import com.hobozoo.aiqq.entity.demo.Cat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.net.HttpURLConnection;
import java.net.URL;

@RestController
@RequestMapping("/demo")
@Tag(name = "Demo")
public class DemoController {

    @Operation(summary = "ping", description = "连通测试")
    @RequestMapping(value = "/ping", method = RequestMethod.POST)
    public Cat findById(@Parameter(description = "cat", required = true) @RequestBody Cat pingCat) {
        Cat cat = new Cat();
        if ("ping".equals(pingCat.getName())){
            cat = Cat.builder().name("pong").build();
        }
        return cat;
    }

    public static void main(String[] args) throws Exception{
        /**
         * todo 那这边应该就2种访问方式
         * 1.握手，发送消息，获得响应，挥手
         * 2.握手，{发送消息，获取响应}，{发送消息，获取响应}...
         * 第二种就是所谓的长连接？
         */
        URL url = new URL("https://www.baidu.com/");
        // 这边已经握手成功了吗？
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        //浏览器告诉Server支持的MIME类型，优先顺序从左往右
        con.setRequestProperty("accept", "text/html, application/xhtml+xml, application/xml; q=0.9, */*; q=0.8");
        //浏览器告诉Server支持的编码，优先顺序从左往右
        con.setRequestProperty("accept-encoding", "gzip, utf-8, deflate, br");
        //浏览器告诉Server支持的语言类型，优先顺序从左往右
        con.setRequestProperty("accept-language", "zh-Hans-CN, zh-Hans; q=0.5");
        //浏览器告诉Server自己这边的情况，如浏览器内核，版本，甚至OS，CPU
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.102 Safari/537.36 Edge/18.18362");
        //浏览器告知Server，这边不支持缓存，静态资源每次访问都需要传递过来，默认private
        con.setRequestProperty("cache-control", "no-cache");
        //看来大致分为告知和期望两个方面的内容：我是hbz，我想要xxx
        //浏览器请求域名（必输）
        con.setRequestProperty("host", "otc-api-hk.eiijo.cn");
        //浏览器期望建立一个长连接
        con.setRequestProperty("connection", "keep-alive");
        //
        con.setRequestProperty("Upgrade-Insecure-Requests", "1");
        URL url2 = new URL("http://www.hbz123.com/index.html?language=cn#j2se");
        HttpURLConnection connection2 = (HttpURLConnection)url.getContent();
        int responseCode1 = connection2.getResponseCode();

    }

}
