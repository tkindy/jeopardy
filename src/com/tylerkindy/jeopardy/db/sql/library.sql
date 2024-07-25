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
select id from categories
order by random()
limit 1;

-- :name get-random-game-with-category :? :1
select distinct(game_id)
from clues
where category_id = :category-id
order by random()
limit 1;

-- :name get-next-category-clue :? :1
select
  cl.id as lib_clue_id,
  ca.name as category,
  g.airdate,
  cl.question,
  cl.answer,
  cl.value
from clues
join categories as ca on cl.category_id = ca.id
join games as g on cl.game_id = g.id
where g.id = :game-id
  and ca.id = :category-id
  and cl.value > :last-value
order by value
limit 1;

-- :name clue-category-info :? :1
select
  g.id as game_id,
  ca.id as category_id,
  cl.value
from clues as cl
join categories as ca on cl.category_id = ca.id
join games as g on cl.game_id = g.id
where cl.id = :clue-id;
