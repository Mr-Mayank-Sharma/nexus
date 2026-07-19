package com.nexus.oms.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaFallbackController {

    @RequestMapping("/")
    public String spaFallback() {
        return "forward:/index.html";
    }
}
