SELECT COUNT(*) FROM customer as c, nation as n WHERE c.stream_part = 'R' AND n.stream_part = 'R' AND  c.c_nationkey = n.n_nationkey;
