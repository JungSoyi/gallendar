package com.gallendar.gradle.server.board.service;

import com.gallendar.gradle.server.board.dto.BoardCreateRequestDto;
import com.gallendar.gradle.server.board.dto.BoardResponseDto;
import com.gallendar.gradle.server.board.dto.BoardUpdateRequestDto;
import com.gallendar.gradle.server.board.entity.Board;
import com.gallendar.gradle.server.board.repository.BoardRepository;
import com.gallendar.gradle.server.board.repository.BoardRepositoryCustomImpl;
import com.gallendar.gradle.server.category.domain.Category;
import com.gallendar.gradle.server.category.domain.CategoryRepository;
import com.gallendar.gradle.server.members.domain.Members;
import com.gallendar.gradle.server.members.domain.MembersRepository;
import com.gallendar.gradle.server.photo.entity.Photo;
import com.gallendar.gradle.server.photo.repository.PhotoRepository;
import com.gallendar.gradle.server.photo.service.S3UploadService;
import com.gallendar.gradle.server.tags.domain.*;
import com.gallendar.gradle.server.tags.domain.BoardTags;
import com.gallendar.gradle.server.tags.type.TagStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService{

    private final BoardRepository boardRepository;
    private final MembersRepository membersRepository;
    private final S3UploadService photoService;
    private final PhotoRepository photoRepository;
    private final BoardTagsRepository boardTagsRepository;
    private final TagsRepository tagsRepository;
    private final CategoryRepository categoryRepository;
    private final BoardRepositoryCustomImpl boardRepositoryCustom;

    /* 게시글 저장 */
    @Transactional
    public Long save(BoardCreateRequestDto requestDto, List<String> tagsMembers) throws IOException {

        Members members = membersRepository.findById(requestDto.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException());

        verifyMember(members);
        String fileName= UUID.randomUUID()+"-"+requestDto.getPhoto().getOriginalFilename();
        String path = photoService.upload(requestDto.getPhoto());
        Photo photo = Photo.builder().fileName(fileName).path(path).build();

        if(categoryRepository.findByCategoryTitle(requestDto.getCategory())==null){
            Category category = Category.builder()
                    .categoryTitle(requestDto.getCategory()).build();
            categoryRepository.save(category);
        }

        Category category = categoryRepository.findByCategoryTitle(requestDto.getCategory());

        Board board = requestDto.toEntity();

        board.setMembers(members);
        board.setPhoto(photo);
        board.setCategory(category);
        boardRepository.save(board);
        photoRepository.save(photo);


        tagsMembers.forEach(m -> {

            BoardTags boardTags = new BoardTags();
            Tags tags = Tags.builder()
                    .tagsMember(m)
                    .tagStatus(TagStatus.alert)
                    .build();
            boardTags.setBoard(board);
            boardTags.setTags(tags);

            boardTagsRepository.save(boardTags);
            tagsRepository.save(tags);

        });
            return null;

    }
    /* 게시글 수정 */
    @Transactional
    public Long update(Long boardId, BoardUpdateRequestDto requestDto, List<String> tagsMembers) {

        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. boardId =" + boardId));

        Optional.ofNullable(requestDto.getTitle())
                .ifPresent(title->requestDto.setTitle(title));
        Optional.ofNullable(requestDto.getContent())
                .ifPresent(content->requestDto.setContent(content));
        Optional.ofNullable(requestDto.getMusic())
                .ifPresent(music->requestDto.setMusic(music));
        Optional.ofNullable(requestDto.getPhoto())
                .ifPresent(photo->requestDto.setPhoto(photo));
        Optional.ofNullable(requestDto.getCategoryTitle())
                        .ifPresent(categoryTitle -> requestDto.setCategoryTitle(categoryTitle));
        Optional.ofNullable(tagsMembers)
                .ifPresent(setTagsMembers -> tagsMembers.forEach(m -> {

                    BoardTags boardTags = new BoardTags();
                    Tags tags = Tags.builder()
                            .tagsMember(m)
                            .tagStatus(TagStatus.alert)
                            .build();
                    boardTags.setBoard(board);
                    boardTags.setTags(tags);

                    boardTagsRepository.save(boardTags);
                    tagsRepository.save(tags);

                }));

        return boardId;
    }

    /* boardId로 게시글 조회 */
    @Transactional
    public BoardResponseDto findById (Long boardId){

        Board entity = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. boardId =" + boardId));
        return new BoardResponseDto(entity);
    }

    /* 전체 게시글 조회 */
    @Transactional(readOnly = true)
    public List<Board> findAllDesc(int page, int size){

        Page<Board> findAllBoard = findAllBoard(page, size);

        List<Board> boards = findAllBoard.getContent();

        return boards;

    }

    public Page<Board> findAllBoard(int page, int size){
        return boardRepository.findAllDescBy(PageRequest.of(page-1, size, Sort.by("boardId").descending()));
    }

    /* 게시글 삭제 */
    @Transactional
    public void delete (Long boardId){
        Board board = boardRepository.findById(boardId).orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. boardId="+boardId));


        /**
         * Todo:
         * tag 상태가 alert인 tag만 tag status를 delete로 바꾼다.
         */
//        BoardTags boardTags = boardTagsRepository.findById(boardId).orElseThrow(() -> new IllegalArgumentException("태그가 없습니다."));


//        Tags tags = tagsRepository.findById(boardId).orElseThrow(() -> new IllegalArgumentException("태그가 없습니다."));
//        tagsRepository.(tags);
//        boardTagsRepository.delete(boardTags);

        /**
         * Todo:
         * board의 categoryId가 같은 board의 갯수가 1개(자기자신)이면 category를 삭제한다.
         * Board의 CategoryId가 categoryId이고 Board의 member가 member인 데이터 찾기.
         */
        int count= boardRepositoryCustom.findByCategoryCount(board.getCategory().getId());
        System.out.println("count = " + count);
        if(count == 1){
            Category category = categoryRepository.findByCategoryTitle(board.getCategory().getCategoryTitle());
            categoryRepository.delete(category);
        }

        boardRepository.delete(board);
    }

    /**
     * Todo:
     * ExceptionCode 작성후 error response 수정
     */
    private void isAuthorized(Board board, Members members){
        if(!board.getMembers().equals(members)) {
            throw new IllegalArgumentException("사용자가 일치하지 않습니다.");
        }
    }

    private void verifyMember(Members members){
        membersRepository.findById(members.getId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

}