SELECT COUNT(*) FROM part as p, partsupp as ps WHERE p.stream_part = 'R' AND ps.stream_part = 'R' AND  p.p_partkey = ps.ps_partkey AND p.p_size=15
