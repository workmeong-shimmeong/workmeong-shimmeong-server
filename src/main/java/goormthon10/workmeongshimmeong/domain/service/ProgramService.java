package goormthon10.workmeongshimmeong.domain.service;

import goormthon10.workmeongshimmeong.api.dto.AddImagesRequest;
import goormthon10.workmeongshimmeong.api.dto.ChatLinkResponse;
import goormthon10.workmeongshimmeong.api.dto.DateResponse;
import goormthon10.workmeongshimmeong.api.dto.EnrollProgramRequest;
import goormthon10.workmeongshimmeong.api.dto.EnrollProgramResponse;
import goormthon10.workmeongshimmeong.api.dto.ImageResponse;
import goormthon10.workmeongshimmeong.api.dto.ProgramInfoResponse;
import goormthon10.workmeongshimmeong.api.dto.ProgramInfosResponse;
import goormthon10.workmeongshimmeong.api.dto.ProgramMinInfoResponse;
import goormthon10.workmeongshimmeong.api.dto.UpdateProgramDetailRequest;
import goormthon10.workmeongshimmeong.common.error.ImageException;
import goormthon10.workmeongshimmeong.common.s3.S3Uploader;
import goormthon10.workmeongshimmeong.domain.embbeded.Location;
import goormthon10.workmeongshimmeong.domain.entity.ImageEntity;
import goormthon10.workmeongshimmeong.domain.entity.Member;
import goormthon10.workmeongshimmeong.domain.entity.Program;
import goormthon10.workmeongshimmeong.domain.enums.ProgramStatus;
import goormthon10.workmeongshimmeong.domain.repository.ImageRepository;
import goormthon10.workmeongshimmeong.domain.repository.MemberRepository;
import goormthon10.workmeongshimmeong.domain.repository.ProgramRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProgramService {

    private static final String IMAGE_DIR = "program";
    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final ProgramRepository programRepository;
    private final ImageRepository imageRepository;
    private final S3Uploader s3Uploader;

    @Transactional
    public EnrollProgramResponse enrollSpace(EnrollProgramRequest request) throws ImageException {
//        Member host = memberService.findMember(request.hostEmail(), request.description(), MemberType.HOST);

//        host.updateDescription(request.description());
//        memberRepository.save(host);

        log.info("[등록자 이메일]: {}", request.getHostEmail());
        log.info("[등록자 소개글]: {}", request.getHostDescription());
        log.info("[프로그램 이름 ]: {}", request.getProgramName());
        log.info("[가격]: {}", request.getPrice());

        Member host = Member.builder()
                .email(request.getHostEmail())
                .name(request.getHostName())
                .description(request.getHostDescription())
                .build();
        memberRepository.save(host);

        Program createdProgram = Program.builder()
                .name(request.getProgramName())
                .roadNameAddress(request.getRoadNameAddress())
                .category(request.getCategory())
                .description(request.getDescription())
                .member(host)
                .startDateTime(request.getStartDate())
                .price(request.getPrice())
                .chatLink(request.getChatLink())
                .spendTime(request.getSpendTime())
                .description(request.getDescription())
                .location(Location.of(request.getLatitude(), request.getLongitude()))
                .build();

        programRepository.save(createdProgram);

        List<ImageEntity> images = new ArrayList<>();

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            for (int i = 0; i < request.getImages().size(); i++) {
                String imageUrl = s3Uploader.uploadFiles(request.getImages().get(i), IMAGE_DIR);
                ImageEntity imageEntity = ImageEntity.of(imageUrl, i, createdProgram);
                images.add(imageEntity);
            }
            imageRepository.saveAll(images);
        }

        return EnrollProgramResponse.from(createdProgram.getProgramNumber());
    }

    public ProgramInfoResponse findProgramInfo(Long id) {
        Program findProgram = programRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 프로그램의 상세 정보 찾을 수 없습니다."));

        List<ImageResponse> images = imageRepository.findAllByProgramId(findProgram.getId()).stream()
                .map(ImageResponse::from)
                .toList();

        return ProgramInfoResponse.of(findProgram, images, findProgram.getMember());
    }

    public ProgramInfosResponse findPrograms() {
        List<ProgramMinInfoResponse> availablePrograms = new ArrayList<>();

        List<ProgramMinInfoResponse> exists = programRepository.findAllByStatus(ProgramStatus.AVAILABLE)
                .stream()
                .map(program -> ProgramMinInfoResponse.of(program, findMainImages(program)))
                .filter(programMinInfoResponse -> programMinInfoResponse.mainImage() != null)
                .sorted(Comparator.comparing(ProgramMinInfoResponse::id).reversed())
                .toList();

        List<ProgramMinInfoResponse> notExists = programRepository.findAllByStatus(ProgramStatus.AVAILABLE)
                .stream()
                .map(program -> ProgramMinInfoResponse.of(program, findMainImages(program)))
                .filter(programMinInfoResponse -> programMinInfoResponse.mainImage() == null)
                .sorted(Comparator.comparing(ProgramMinInfoResponse::id).reversed())
                .toList();

        availablePrograms.addAll(exists);
        availablePrograms.addAll(notExists);

        return ProgramInfosResponse.from(availablePrograms);
    }

    private String findMainImages(Program program) {
        Optional<ImageEntity> maybeMainImage = imageRepository.findByProgramIdAndOrder(program.getId(), 0);
        return maybeMainImage.map(ImageEntity::getUrl).orElse(null);
    }


    public DateResponse getAvailableDate(Long id) {
        Program findProgram = programRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 게시물을 찾을 수 없습니다."));
        return DateResponse.from(findProgram);
    }

    public ChatLinkResponse findChatLink(Long id) {
        Program findProgram = programRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 프로그램은 존재하지 않습니다."));
        return new ChatLinkResponse(findProgram.getChatLink());
    }

    @Transactional
    public void addImages(AddImagesRequest request, Long id) throws ImageException {
        Program findProgram = programRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 id의 프로그램을 찾을 수 없습니다."));

        int maxOrder = imageRepository.findAllByProgramId(id).stream()
                .map(ImageEntity::getOrder)
                .max(Comparator.comparing(Integer::intValue))
                .orElse(0);

        List<ImageEntity> imageEntities = new ArrayList<>();
        for (int i = 0; i < request.images().size(); i++) {
            String url = s3Uploader.uploadFiles(request.images().get(i), IMAGE_DIR);
            imageEntities.add(ImageEntity.of(url, maxOrder + i + 1, findProgram));
        }

        imageRepository.saveAll(imageEntities);
    }

    @Transactional
    public void updateDetails(UpdateProgramDetailRequest request) {
        Program findProgram = programRepository.findById(request.programId())
                .orElseThrow(() -> new EntityNotFoundException("해당 프로그램을 찾을 수 없습니다."));

        findProgram.updateDescription(request.programDescription());
        findProgram.updateRoadNameAddress(request.roadNameAddress());
        findProgram.getMember().updateDescription(request.hostDescription());

        programRepository.save(findProgram);
        memberRepository.save(findProgram.getMember());
    }
}
