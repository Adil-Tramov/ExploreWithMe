CREATE TABLE IF NOT EXISTS event_ratings (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating_type VARCHAR(10) NOT NULL CHECK (rating_type IN ('LIKE', 'DISLIKE')),
    created TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(event_id, user_id)
);