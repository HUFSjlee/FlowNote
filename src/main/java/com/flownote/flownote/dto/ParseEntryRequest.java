package com.flownote.flownote.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ParseEntryRequest {
    //사용자가 입력한 텍스트
    private String text;
    //(선택) 연동 ON일 때만 true
    private Boolean syncToGoogle;
}
