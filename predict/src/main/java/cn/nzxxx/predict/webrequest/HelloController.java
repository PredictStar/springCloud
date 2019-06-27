package cn.nzxxx.predict.webrequest;

import org.jboss.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HelloController {
    private final Logger logger=Logger.getLogger(this.getClass());
    @RequestMapping(value="/test/aa")
    @ResponseBody
    public List testBB(String str) throws Exception{
        System.out.println(str+"xxxx");
        List list=new ArrayList();
        Map map=new HashMap();
        map.put("value","12");
        map.put("text","ggg");
        Map map2=new HashMap();
        map2.put("value","2222");
        map2.put("text","uuuuu");
        list.add(map);
        list.add(map2);
        return list;
    }
    @RequestMapping("/hello")
    public String hello(ModelMap map) {
        // 加入一个属性，用来在模板中读取
        map.addAttribute("name", "li");
        map.addAttribute("bookTitle", "近代简史");
        return "/hello";
    }
}
