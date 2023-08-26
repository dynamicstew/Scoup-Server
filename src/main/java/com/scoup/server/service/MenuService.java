package com.scoup.server.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.scoup.server.common.response.ErrorMessage;
import com.scoup.server.controller.exception.NotFoundException;
import com.scoup.server.domain.Cafe;
import com.scoup.server.domain.Menu;
import com.scoup.server.domain.UserOrder;
import com.scoup.server.dto.menu.MenuResponseDto;
import com.scoup.server.repository.MenuRepository;
import com.scoup.server.repository.UserOrderRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;
	private final UserOrderRepository userOrderRepository;

	public MenuResponseDto getMenuList(Long shopId, Long orderId){
		List<Menu> menuList=menuRepository.findByCafe_Id(shopId);

		UserOrder userOrder=userOrderRepository.findById(orderId)
				.orElseThrow(() -> new NotFoundException(ErrorMessage.NOT_FOUND_USER_EXCEPTION));

		List<String> orderMenus=new ArrayList<>();

		for(int i=0; i<menuList.size(); i++){
			if(menuList.get(i).getUserOrderList().contains(userOrder)){
				orderMenus.add(menuList.get(i).getName());

			}
		}

		if(orderMenus.isEmpty()){
			throw new NotFoundException(ErrorMessage.NOT_FOUND_MENU_EXCEPTION);
		}

		return MenuResponseDto.builder()
				.menu(orderMenus)
				.build();
	}

}