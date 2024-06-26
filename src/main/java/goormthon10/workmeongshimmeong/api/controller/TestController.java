package goormthon10.workmeongshimmeong.api.controller;

import goormthon10.workmeongshimmeong.domain.service.InitService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Test", description = "테스트 관련 API 입니다.")
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final InitService initService;
    @ApiResponse(description = "상태 체크 API")
    @GetMapping
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/init")
    public ResponseEntity<Void> initData(){
        initService.initData();
        return ResponseEntity.noContent().build();
    }
}
