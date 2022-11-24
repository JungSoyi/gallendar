package com.gallendar.gradle.server.board.dto;

import com.gallendar.gradle.server.board.entity.Board;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

@Getter
@NoArgsConstructor
public class BoardCreateRequestDto {

    @NotBlank(message = "제목을 입력하세요.")
    private String title;

    @NotBlank(message = "내용을 입력하세요.")
    private String content;
    private String music;
    private Long boardId;

    @Builder
    public BoardCreateRequestDto(String title, String content, String music, Long boardId){
        this.title = title;
        this.content = content;
        this.music = music;
        this.boardId = boardId;
    }

    public Board toEntity(){
        return Board.builder()
                .title(title)
                .content(content)
                .music(music)
                .photoId(boardId)
                .build();
    }
}
