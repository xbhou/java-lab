DROP DATABASE IF EXISTS index_lab;
CREATE DATABASE index_lab;
USE index_lab;

CREATE TABLE digits (
    d TINYINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO digits(d)
VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id INT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    note VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

INSERT INTO orders(
    tenant_id,
    user_id,
    status,
    created_at,
    amount,
    currency,
    note
)
SELECT
    MOD(n * 17, 100) + 1,
    1000000 + MOD(n * 37, 50000),
    CASE MOD(FLOOR(n / 100), 20)
        WHEN 0 THEN 'PAID'
        WHEN 1 THEN 'PAID'
        WHEN 2 THEN 'PAID'
        WHEN 3 THEN 'PAID'
        WHEN 4 THEN 'PAID'
        WHEN 5 THEN 'PAID'
        WHEN 6 THEN 'PAID'
        WHEN 7 THEN 'PAID'
        WHEN 8 THEN 'PAID'
        WHEN 9 THEN 'PAID'
        WHEN 10 THEN 'PENDING'
        WHEN 11 THEN 'PENDING'
        WHEN 12 THEN 'PENDING'
        WHEN 13 THEN 'PENDING'
        WHEN 14 THEN 'PENDING'
        WHEN 15 THEN 'CANCELLED'
        WHEN 16 THEN 'CANCELLED'
        WHEN 17 THEN 'CANCELLED'
        ELSE 'REFUNDED'
    END,
    DATE_ADD(
        DATE_ADD(
            '2025-01-01 00:00:00',
            INTERVAL MOD(n * 37, 730) DAY
        ),
        INTERVAL MOD(n * 97, 86400) SECOND
    ),
    ROUND((MOD(n * 13, 500000) + 100) / 100, 2),
    CASE MOD(n, 4)
        WHEN 0 THEN 'USD'
        WHEN 1 THEN 'EUR'
        WHEN 2 THEN 'MYR'
        ELSE 'SGD'
    END,
    CASE
        WHEN MOD(n, 37) = 0
            THEN CONCAT('vip-order-', n)
        ELSE CONCAT('normal-order-', n)
    END
FROM (
    SELECT
        d0.d
        + d1.d * 10
        + d2.d * 100
        + d3.d * 1000
        + d4.d * 10000
        + d5.d * 100000 AS n
    FROM digits d0
    CROSS JOIN digits d1
    CROSS JOIN digits d2
    CROSS JOIN digits d3
    CROSS JOIN digits d4
    CROSS JOIN digits d5
) seq
WHERE n BETWEEN 1 AND 200000;

DROP TABLE digits;

ANALYZE TABLE orders;

SELECT COUNT(*) AS row_count FROM orders;
SELECT status, COUNT(*) AS rows_per_status
FROM orders
GROUP BY status
ORDER BY rows_per_status DESC;
