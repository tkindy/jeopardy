-- :name get-random-clues :? :*
select
  cl.id as lib_clue_id,
  ca.name as category,
  g.airdate,
  cl.question,
  cl.answer,
  cl.value
from clues as cl
join categories as ca on cl.category_id = ca.id
join games as g on cl.game_id = g.id
order by random()
limit :limit

-- :name get-random-category :? :1
select * from categories
order by random()
limit 1;

-- :name get-random-game-with-category :? :1
select distinct(game_id)
from clues
where category_id = :category-id
order by random()
limit 1;

-- :name get-next-category-clue :? :1
select * from clues
where game_id = :game-id
  and category_id = :category-id
  and value > :last-value
order by value
limit 1;
