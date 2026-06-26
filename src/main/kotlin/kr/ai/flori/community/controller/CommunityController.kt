package kr.ai.flori.community.controller

import jakarta.validation.Valid
import kr.ai.flori.community.dto.CommentCreateRequest
import kr.ai.flori.community.dto.CommentResponse
import kr.ai.flori.community.dto.CommentUpdateRequest
import kr.ai.flori.community.dto.CommunityUploadTargetResponse
import kr.ai.flori.community.dto.CommunityUploadTargetsRequest
import kr.ai.flori.community.dto.LikeToggleResponse
import kr.ai.flori.community.dto.PinRequest
import kr.ai.flori.community.dto.PostCreateRequest
import kr.ai.flori.community.dto.PostResponse
import kr.ai.flori.community.dto.PostUpdateRequest
import kr.ai.flori.community.dto.PostsPageResponse
import kr.ai.flori.community.dto.ReportCreateRequest
import kr.ai.flori.community.service.CommunityService
import kr.ai.flori.community.service.CommunityUploadService
import kr.ai.flori.verification.gating.RequiresBusinessVerified
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 커뮤니티 게시판. 단일 커뮤니티(테넌트 간 공유). 모든 엔드포인트 JWT 인증.
 * 권한/마스킹/소유권은 서버가 뷰어(JWT) 기준으로 계산해 응답에 포함한다.
 */
@RestController
@RequestMapping("/community")
@RequiresBusinessVerified
class CommunityController(
    private val communityService: CommunityService,
    private val communityUploadService: CommunityUploadService,
) {
    @GetMapping("/posts")
    fun listPosts(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ): PostsPageResponse = communityService.listPosts(category, search, offset, limit)

    @GetMapping("/posts/{id}")
    fun getPost(
        @PathVariable id: Long,
    ): PostResponse = communityService.getPost(id)

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPost(
        @Valid @RequestBody request: PostCreateRequest,
    ): PostResponse = communityService.createPost(request)

    @PatchMapping("/posts/{id}")
    fun updatePost(
        @PathVariable id: Long,
        @Valid @RequestBody request: PostUpdateRequest,
    ): PostResponse = communityService.updatePost(id, request)

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePost(
        @PathVariable id: Long,
    ) {
        communityService.deletePost(id)
    }

    @PostMapping("/posts/{id}/like")
    fun toggleLike(
        @PathVariable id: Long,
    ): LikeToggleResponse = communityService.toggleLike(id)

    /** 게시글 고정/해제(관리자 전용 — 서비스에서 강제). */
    @PostMapping("/posts/{id}/pin")
    fun setPinned(
        @PathVariable id: Long,
        @Valid @RequestBody request: PinRequest,
    ): PostResponse = communityService.setPinned(id, requireNotNull(request.pinned))

    @PostMapping("/upload-targets")
    fun uploadTargets(
        @Valid @RequestBody request: CommunityUploadTargetsRequest,
    ): List<CommunityUploadTargetResponse> = communityUploadService.createUploadTargets(requireNotNull(request.files))

    @GetMapping("/posts/{id}/comments")
    fun listComments(
        @PathVariable id: Long,
    ): List<CommentResponse> = communityService.listComments(id)

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun createComment(
        @PathVariable id: Long,
        @Valid @RequestBody request: CommentCreateRequest,
    ): CommentResponse = communityService.createComment(id, request)

    @PatchMapping("/comments/{id}")
    fun updateComment(
        @PathVariable id: Long,
        @Valid @RequestBody request: CommentUpdateRequest,
    ): CommentResponse = communityService.updateComment(id, request)

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComment(
        @PathVariable id: Long,
    ) {
        communityService.deleteComment(id)
    }

    @PostMapping("/posts/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reportPost(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReportCreateRequest,
    ) {
        communityService.reportPost(id, request)
    }

    @PostMapping("/comments/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reportComment(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReportCreateRequest,
    ) {
        communityService.reportComment(id, request)
    }
}
