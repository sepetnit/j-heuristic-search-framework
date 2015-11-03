
locs = ['a', 'b', 'c', 'd', 'e']
shortest = {
	'a': {'a': 0, 'b': 3, 'c': 1, 'd': 10, 'e': 100},
	'b': {'b': 0, 'a': 3, 'c': 2, 'd': 3, 'e': 100},
	'c': {'c': 0, 'a': 1, 'b': 2, 'd': 4, 'e': 1},
	'd': {'d': 0, 'a': 10, 'b': 3, 'c': 4, 'e': 1},
	'e': {'e': 0, 'a': 100, 'b':100, 'c': 1, 'd': 1}
}


def main():
	for k in xrange(len(locs)):
		for i in xrange(len(locs)):
			for j in xrange(len(locs)):
				c = shortest[locs[i]][locs[k]] + shortest[locs[k]][locs[j]]
				if c < shortest[locs[i]][locs[j]]:
					print 'updating %c->%c to be %d (was %d before)' % (locs[i], locs[j], c, shortest[locs[i]][locs[j]])
					print 'shortest path is %c->%c->%c' % (locs[i], locs[k], locs[j])
					shortest[locs[i]][locs[j]] = c

if __name__ == '__main__':
	main()