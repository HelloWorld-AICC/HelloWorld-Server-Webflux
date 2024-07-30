package Helloworld.helloworld_webflux.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class UserCustomRepositoryImpl implements UserCustomRepository{
    private final DatabaseClient databaseClient;
    @Override
    public Mono<String> findLanguageByUserId(Long userId) {
        var sql= """
                SELECT l.name
                FROM summary.language l
                INNER JOIN summary.user_language ul ON l.id = ul.language_id
                WHERE ul.user_id = :userId
                """;
        return databaseClient.sql(sql)
                .bind("userId", userId)
                .map(row -> row.get("name", String.class))
                .one();  // 결과가 하나인 것을 기대하며 Mono로 반환
    }
}
