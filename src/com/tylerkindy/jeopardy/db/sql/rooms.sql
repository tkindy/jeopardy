-- :name insert-room :! :n
INSERT INTO rooms (id, mode)
VALUES (:id, :mode);

-- :name get-plan :? :1
SELECT * FROM plans
WHERE id = :id;
