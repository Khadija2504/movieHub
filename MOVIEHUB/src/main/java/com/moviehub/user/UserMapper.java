package com.moviehub.user;

import org.mapstruct.Mapper;

import com.moviehub.user.dto.UserResponse;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
