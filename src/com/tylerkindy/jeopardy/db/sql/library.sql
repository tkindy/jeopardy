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
