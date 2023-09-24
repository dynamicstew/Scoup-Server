package com.scoup.server.service;

import com.scoup.server.common.response.ErrorMessage;
import com.scoup.server.controller.exception.NotFoundDataException;
import com.scoup.server.domain.Cafe;
import com.scoup.server.domain.Event;
import com.scoup.server.domain.Menu;
import com.scoup.server.domain.Stamp;
import com.scoup.server.domain.User;
import com.scoup.server.domain.UserOrder;
import com.scoup.server.dto.Event.EventResponseDto;
import com.scoup.server.dto.admin.PatchAdminCafeRequestDto;
import com.scoup.server.dto.cafe.AdminCafeRequestDto;
import com.scoup.server.dto.cafe.SearchCafeResponseDto;
import com.scoup.server.dto.menu.ReceiptRequestDto;
import com.scoup.server.dto.menu.ReceiptResponseDto;
import com.scoup.server.repository.CafeRepository;
import com.scoup.server.repository.EventRepository;
import com.scoup.server.repository.MenuRepository;
import com.scoup.server.repository.StampRepository;
import com.scoup.server.repository.UserOrderRepository;
import com.scoup.server.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Builder
@RequiredArgsConstructor
@Transactional(readOnly = false)
public class CafeService {

    private final UserRepository userRepository;
    private final CafeRepository cafeRepository;
    private final EventRepository eventRepository;
    private final UserOrderRepository userOrderRepository;
    private final MenuRepository menuRepository;
    private final StampRepository stampRepository;

    public List<SearchCafeResponseDto> searchCafe(Long userId, String keyword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));

        List<Cafe> cafeList = cafeRepository.findAllCafeContainingKeyword(keyword);

        return cafeList.stream()
            .map(SearchCafeResponseDto::of)
            .collect(Collectors.toList());
    }

    public void addCafe(Long userId, Long shopId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));

        if (user.getCafeIdList().contains(shopId)) {
            throw new NotFoundDataException(ErrorMessage.REDUPLICATION_CAFE_EXCEPTION);
        } else {
            user.getCafeIdList().add(shopId);
        }
    }

    @Transactional
    public void deleteCafe(Long cafeId, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));
        Cafe cafe = cafeRepository.findById(cafeId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_CAFE_EXCEPTION));

        cafeRepository.deleteById(cafe.getId());
    }

    public List<EventResponseDto> getEvent(Long cafeId) {
        Cafe cafe = this.cafeRepository.findById(cafeId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_CAFE_EXCEPTION));

        List<Event> eventList = cafe.getEventList();
        List<EventResponseDto> dtoList = new ArrayList<>();

        for (int i = 0; i < eventList.size(); i++) {
            EventResponseDto tmp = EventResponseDto.builder()
                .eventId(eventList.get(i).getId())
                .content(eventList.get(i).getContent())
                .createdAt(eventList.get(i).getCreatedAt())
                .build();
            dtoList.add(tmp);
        }

        return dtoList;
    }

    public void addEvent(Long cafeId, String content, Long adminUserId) {

        User adminUser=userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));

        if(!adminUser.getMaster()){
            throw new NotFoundDataException(ErrorMessage.NOT_ADMIN_EXCEPTION);
        }

        Cafe cafe = this.cafeRepository.findById(cafeId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_CAFE_EXCEPTION));

        Event event = Event.builder()
            .createdAt(LocalDateTime.now())
            .content(content)
            .cafe(cafe)
            .build();

        eventRepository.save(event);
        cafe.getEventList().add(event);
    }

    public void deleteMainPageCafe(Long userId, Long cafeId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));

        //카페 아이디 리스트 뽑아와서 있으면 삭제
        if (user.getCafeIdList().contains(cafeId)) {
            user.getCafeIdList().remove(cafeId);
        } else {
            throw new NotFoundDataException(ErrorMessage.NOT_FOUND_CAFE_EXCEPTION);
        }

        userRepository.save(user);
    }

    @Transactional
    public ReceiptResponseDto postReceipt(ReceiptRequestDto requestDto, Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));
        Cafe cafe = cafeRepository.findByName(requestDto.getStore())
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_CAFE_EXCEPTION));

        Stamp stamp = stampRepository.save(Stamp.builder()
            .user(user)
            .cafe(cafe)
            .cardName(requestDto.getCardName())
            .cardNum(requestDto.getCardNum())
            .build());

        requestDto.getItems().stream()
            .forEach(menu -> {
                Optional<Menu> menuData = menuRepository.findByName(menu.getName());

                if (menuData.isEmpty()) {
                    Menu saveMenu = menuRepository.save(Menu.builder()
                        .name(menu.getName())
                        .price(menu.getPrice())
                        .imageUrl("123")
                        .cafe(cafe)
                        .build());
                    stampSave(saveMenu, user, stamp);
                } else {
                    stampSave(menuData.get(), user, stamp);
                }
            });

        return ReceiptResponseDto.builder()
            .store(cafe.getName())
            .cardNum(requestDto.getCardNum())
            .cardName(requestDto.getCardName())
            .items(requestDto.getItems())
            .stampId(stamp.getId())
            .build();
    }

    public void stampSave(Menu menu, User user, Stamp stamp) {
        userOrderRepository.save(UserOrder.builder()
            .user(user)
            .menu(menu)
            .stamp(stamp)
            .build());
    }

    @Transactional
    public void patchCafe(PatchAdminCafeRequestDto requestDto, Long shopId, Long userId){
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));
        Cafe cafe = cafeRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_CAFE_EXCEPTION));

        cafe.patchCafe(requestDto);
    }

    public void addAdminCafe(Long adminUserId, AdminCafeRequestDto adminCafeRequestDto){
        User adminUser=userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundDataException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));

        if(!adminUser.getMaster()){
            throw new NotFoundDataException(ErrorMessage.NOT_ADMIN_EXCEPTION);
        }

        Cafe cafe=Cafe.builder()
                .name(adminCafeRequestDto.getShopName())
                .phoneNumber(adminCafeRequestDto.getPhoneNumber())
                .licenseeNumber(adminCafeRequestDto.getLicenseeNumber())
                .runningTime(adminCafeRequestDto.getRunningTime())
                .location(adminCafeRequestDto.getShopAddress())
                .imageUrl(adminCafeRequestDto.getShopImageUrl())
                .build();

        cafeRepository.save(cafe);
    }
}
