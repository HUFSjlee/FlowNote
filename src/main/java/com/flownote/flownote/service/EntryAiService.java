package com.flownote.flownote.service;

import com.flownote.flownote.entity.Entry;
import org.springframework.stereotype.Service;

@Service
public class EntryAiService {

    /**
     * TODO: 나중에 OpenAI API 붙여서
     * - rawContent(텍스트) 기준으로
     *   - type(EXPENSE / SCHEDULE / NOTE)
     *   - price
     *   - startDateTime
     *   - location
     *   등을 채워 넣는 로직을 여기에 구현
     */
    public void enrichEntryWithAi(Entry entry) {
        // 지금은 비워두거나, 임시 룰 베이스로 써도 됨.
    }
}
