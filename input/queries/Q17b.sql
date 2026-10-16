SELECT COUNT(*) FROM part as p, lineitem as l WHERE p.stream_part = 'R' AND l.stream_part = 'R' AND  p.p_partkey = l.l_partkey AND p.p_brand = 'Brand#23';
