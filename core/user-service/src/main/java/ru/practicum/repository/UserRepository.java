package ru.practicum.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.model.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    @Query("SELECT new ru.practicum.model.User(u.id, u.email, u.name) " +
            "FROM User u " +
            "WHERE u.id  IN :ids " +
            "ORDER BY u.id")
    List<User> getUsers(@Param("ids") List<Long> ids);

    @Query(value = "SELECT new ru.practicum.model.User(u.id, u.email, u.name) " +
            "FROM User u " +
            "ORDER BY id " +
            "LIMIT :size OFFSET :from")
    List<User> getUsers(@Param("from") Long from,
                        @Param("size") Long size);
}
