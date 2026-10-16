SELECT COUNT(*) FROM supplier as s, nation as n WHERE s.stream_part = 'R' AND n.stream_part = 'R' AND  s.s_nationkey = n.n_nationkey;
