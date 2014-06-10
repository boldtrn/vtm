package org.oscim.utils.quadtree;

import java.util.Arrays;

import org.oscim.utils.SpatialIndex.SearchCb;
import org.oscim.utils.pool.Inlist;
import org.oscim.utils.pool.Pool;
import org.oscim.utils.quadtree.BoxTree.BoxItem;
import org.oscim.utils.quadtree.BoxTree.BoxNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A BoxTree is made of BoxNodes which hold a list of
 * generic BoxItems which can hold a custom data item.
 * 
 * ... in case this generic isnt obvious at first sight.
 * */
public class BoxTree<T extends BoxItem<E>, E> extends TileIndex<BoxNode<T>, T> {

	final static Logger log = LoggerFactory.getLogger(BoxTree.class);
	static boolean dbg = false;

	protected final int extents;
	protected final int maxDepth;
	private final static int MAX_STACK = 32;

	static class Stack<E> extends Inlist<Stack<E>> {
		/** Top of stack index */
		int tos;

		final E[] nodes;

		@SuppressWarnings("unchecked")
		Stack() {
			nodes = (E[]) new BoxNode[MAX_STACK];
		}

		void push(E node) {
			nodes[tos] = node;
			tos++;
		}

		/** Pop element off iteration stack (For internal use only) */
		E pop() {
			// assert (tos > 0);
			nodes[tos--] = null;
			return (E) nodes[tos];
		}

		E node() {
			return (E) nodes[tos];
		}

		boolean empty() {
			return tos <= 0;
		}
	}

	public static class BoxNode<T extends BoxItem<?>> extends TreeNode<BoxNode<T>, T> {
		// TODO this is redundant - use tile ids
		public int x1;
		public int y1;
		public int x2;
		public int y2;

		// inherits BoxItem<E> item;

		@Override
		public String toString() {
			return x1 + ":" + y1 + ":" + (x2 - x1);
		}
	}

	public static class BoxItem<T> extends Inlist<BoxItem<T>> {
		public BoxItem(int x1, int y1, int x2, int y2) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
		}

		public BoxItem(org.oscim.core.Box box, T item) {
			this.x1 = (int) box.minX;
			this.y1 = (int) box.minY;
			this.x2 = (int) box.maxX;
			this.y2 = (int) box.maxY;
			this.item = item;
		}

		@Override
		public String toString() {
			return x1 + "," + y1 + "/" + x2 + "," + y2;
		}

		public T item;

		public BoxItem() {

		}

		public int x1;
		public int x2;
		public int y1;
		public int y2;

		public boolean overlaps(BoxItem<T> it) {
			return !((x1 > it.x2) || (it.x1 > x2) || (y1 > it.y2) || (it.y1 > y2));
		}
	}

	public interface Visitor<T> {
		boolean process(T item);
	}

	boolean isPowerOfTwo(int x) {
		return ((x > 0) && (x & (x - 1)) == 0);
	}

	public BoxTree(int extents, int maxDepth) {
		super();
		if (!isPowerOfTwo(extents))
			throw new IllegalArgumentException("Extents must be power of two!");

		//size is -extents to +extents
		this.root.x1 = -extents;
		this.root.y1 = -extents;
		this.root.x2 = extents;
		this.root.y2 = extents;

		this.extents = extents;
		this.maxDepth = maxDepth;
	}

	@Override
	public BoxNode<T> create() {
		return new BoxNode<T>();
	}

	@Override
	public void removeItem(T item) {

	}

	Pool<Stack<BoxNode<T>>> stackPool = new Pool<Stack<BoxNode<T>>>() {
		@Override
		protected Stack<BoxNode<T>> createItem() {
			return new Stack<BoxNode<T>>();
		}

		protected boolean clearItem(Stack<BoxNode<T>> item) {
			if (item.tos != 0) {
				item.tos = 0;
				Arrays.fill(item.nodes, null);
			}
			return true;
		};
	};

	public boolean search(T box, SearchCb<E> cb, Object ctxt) {
		BoxNode<T> n;

		Stack<BoxNode<T>> stack = stackPool.get();
		stack.push(root);

		O: while (!stack.empty()) {

			n = stack.pop();

			/* process overlapping items from cur node */
			for (BoxItem<E> it = n.item; it != null; it = it.next) {
				if (it.overlaps(box)) {
					if (!cb.call(it.item, ctxt))
						break O;
				}
			}

			BoxNode<T> p = n.parent;

			/* push next node on same level onto stack */
			switch (n.id) {
				case 0:
					if (overlaps(p.child01, box)) {
						stack.push(p.child01);
						break;
					}
				case 1:
					if (overlaps(p.child10, box)) {
						stack.push(p.child10);
						break;
					}
				case 2:
					if (overlaps(p.child11, box)) {
						stack.push(p.child11);
						break;
					}
				default:
					break;
			}
			/* push next level child onto stack */
			if (overlaps(n.child00, box))
				stack.push(n.child00);
			else if (overlaps(n.child01, box))
				stack.push(n.child01);
			else if (overlaps(n.child10, box))
				stack.push(n.child10);
			else if (overlaps(n.child11, box))
				stack.push(n.child11);
		}
		stackPool.release(stack);
		return false;
	}

	public interface SearchBoxCb<T extends BoxItem<?>> {
		boolean call(T item);
	}

	public boolean search(T box, SearchBoxCb<T> cb) {
		BoxNode<T> n;
		BoxNode<T> start = getNode(box, false);
		if (start == null)
			return false;

		Stack<BoxNode<T>> stack = stackPool.get();
		stack.push(start);

		while (!stack.empty()) {
			n = stack.pop();

			/* process overlapping items from cur node */
			for (BoxItem<E> it = n.item; it != null; it = it.next) {
				if (it.overlaps(box)) {
					@SuppressWarnings("unchecked")
					T item = (T) it;
					if (!cb.call(item)) {
						stackPool.release(stack);
						return false;
					}
				}
			}

			BoxNode<T> p = n.parent;

			/* push next node on same level onto stack */
			switch (n.id) {
				case 0:
					if (overlaps(p.child01, box)) {
						stack.push(p.child01);
						break;
					}
				case 1:
					if (overlaps(p.child10, box)) {
						stack.push(p.child10);
						break;
					}
				case 2:
					if (overlaps(p.child11, box)) {
						stack.push(p.child11);
						break;
					}
				default:
					break;
			}

			/* push next level child onto stack */
			if (overlaps(n.child00, box))
				stack.push(n.child00);
			else if (overlaps(n.child01, box))
				stack.push(n.child01);
			else if (overlaps(n.child10, box))
				stack.push(n.child10);
			else if (overlaps(n.child11, box))
				stack.push(n.child11);
		}
		stackPool.release(stack);

		return true;
	}

	private static boolean overlaps(BoxNode<?> a, BoxItem<?> b) {
		return a != null && !((a.x1 > b.x2) || (b.x1 > a.x2) || (a.y1 > b.y2) || (b.y1 > a.y2));
	}

	public interface SearchNodeCb<E extends BoxNode<?>> {
		boolean call(E item);
	}

	public boolean collect(SearchNodeCb<BoxNode<T>> cb) {
		BoxNode<T> n;

		Stack<BoxNode<T>> stack = stackPool.get();

		stack.push(root);

		while (!stack.empty()) {
			n = stack.pop();

			/* process overlapping items from cur node */
			cb.call(n);

			BoxNode<T> p = n.parent;

			/* push next node on same level onto stack */
			switch (n.id) {
				case 0:
					if (p.child01 != null) {
						stack.push(p.child01);
						break;
					}
				case 1:
					if (p.child10 != null) {
						stack.push(p.child10);
						break;
					}
				case 2:
					if (p.child11 != null) {
						stack.push(p.child11);
						break;
					}
				default:
					break;
			}

			/* push next level child onto stack */
			if (n.child00 != null)
				stack.push(n.child00);
			else if (n.child01 != null)
				stack.push(n.child01);
			else if (n.child10 != null)
				stack.push(n.child10);
			else if (n.child11 != null)
				stack.push(n.child11);
		}
		stackPool.release(stack);
		return false;
	}

	public BoxNode<T> create(BoxNode<T> parent, int i) {
		BoxNode<T> node;
		if (pool != null) {
			node = pool;
			pool = pool.parent;
		} else
			node = new BoxNode<T>();

		node.parent = parent;

		int size = (parent.x2 - parent.x1) >> 1;
		node.x1 = parent.x1;
		node.y1 = parent.y1;

		if (i == 0) {
			parent.child00 = node;
		} else if (i == 1) {
			parent.child01 = node;
			node.y1 += size;
		} else if (i == 2) {
			parent.child10 = node;
			node.x1 += size;
		} else {
			parent.child11 = node;
			node.x1 += size;
			node.y1 += size;
		}

		node.x2 = node.x1 + size;
		node.y2 = node.y1 + size;
		node.id = (byte) i;

		return node;
	}

	public void insert(T box) {
		if (box.x1 > box.x2 || box.y1 > box.y2)
			throw new IllegalArgumentException();

		if (box.next != null)
			throw new IllegalStateException("BoxItem is list");

		BoxNode<T> cur = root;
		BoxNode<T> child = null;

		int x1 = box.x1;
		int x2 = box.x2;
		int y1 = box.y1;
		int y2 = box.y2;

		for (int level = 0; level <= maxDepth; level++) {
			cur.refs++;

			/* half size of tile at current level */
			int hsize = (cur.x2 - cur.x1) >> 1;

			/* center of tile */
			int cx = cur.x1 + hsize;
			int cy = cur.y1 + hsize;

			child = null;

			if (level < maxDepth) {
				if (x2 < cx) {
					if (y2 < cy) {
						if ((child = cur.child00) == null)
							child = create(cur, 0);
					} else if (y1 >= cy) {
						if ((child = cur.child01) == null)
							child = create(cur, 1);
					}
				}
				if (x1 >= cx) {
					if (y2 < cy) {
						if ((child = cur.child10) == null)
							child = create(cur, 2);
					} else if (y1 >= cy) {
						if ((child = cur.child11) == null)
							child = create(cur, 3);
					}
				}
			}

			if (child == null) {
				/* push item onto list of this node */
				box.next = cur.item;
				cur.item = box;

				if (dbg)
					log.debug("insert: " + level
					        + " cnt:" + Inlist.size(cur.item) + " " + x1 + ":" + y1
					        + " /" + (x2) + "x" + (y2) + " " + box.item);
				break;
			}
			cur = child;
		}
	}

	public boolean remove(T box, E item) {
		if (box.x1 > box.x2 || box.y1 > box.y2)
			throw new IllegalArgumentException();

		BoxNode<T> cur = root;
		BoxNode<T> child = null;

		int x1 = box.x1;
		int x2 = box.x2;
		int y1 = box.y1;
		int y2 = box.y2;

		for (int level = 0; level <= maxDepth; level++) {
			/* half size of tile at current level */
			int hsize = (cur.x2 - cur.x1) >> 1;

			/* center of tile */
			int cx = cur.x1 + hsize;
			int cy = cur.y1 + hsize;

			child = null;
			if (level < maxDepth) {
				if (x2 < cx) {
					if (y2 < cy) {
						if ((child = cur.child00) == null)
							return false;
					} else if (y1 >= cy) {
						if ((child = cur.child01) == null)
							return false;
					}
				} else if (x1 >= cx) {
					if (y2 < cy) {
						if ((child = cur.child10) == null)
							return false;
					} else if (y1 >= cy) {
						if ((child = cur.child11) == null)
							return false;
					}
				}
			}
			if (child == null) {
				/* push item onto list of this node */
				BoxItem<E> prev = cur.item;

				for (BoxItem<E> it = cur.item; it != null; it = it.next) {
					if (it.item == item) {
						if (dbg)
							log.debug("remove: " + level
							        + " cnt:" + Inlist.size(cur.item) + " " + x1 + ":" + y1
							        + " /" + (x2) + "x" + (y2) + " " + item);

						if (cur.item == it) {
							// FUNKY GENERICS...
							@SuppressWarnings("unchecked")
							T b = (T) it.next;
							cur.item = b;
						} else
							prev.next = it.next;

						remove(cur);
						return true;
					}
					prev = it;
				}
				return false;
			}

			cur = child;
		}
		return false;
	}

	public BoxNode<T> getNode(T box, boolean create) {
		if (box.x1 > box.x2 || box.y1 > box.y2)
			throw new IllegalArgumentException();

		BoxNode<T> cur = root;
		BoxNode<T> child = null;

		int x1 = box.x1;
		int x2 = box.x2;
		int y1 = box.y1;
		int y2 = box.y2;

		for (int level = 0; level <= maxDepth; level++) {
			cur.refs++;

			/* half size of tile at current z */
			int hsize = (cur.x2 - cur.x1) >> 1;

			/* center of tile (shift by -extents) */
			int cx = cur.x1 + hsize;
			int cy = cur.y1 + hsize;

			child = null;

			if (x2 < cx) {
				if (y2 < cy) {
					if ((child = cur.child00) == null && create)
						child = create(cur, 0);
				} else if (y1 >= cy) {
					if ((child = cur.child01) == null && create)
						child = create(cur, 1);
				}
			}
			if (x1 >= cx) {
				if (y2 < cy) {
					if ((child = cur.child10) == null && create)
						child = create(cur, 2);
				} else if (y1 >= cy) {
					if ((child = cur.child11) == null && create)
						child = create(cur, 3);
				}
			}

			if (child == null || level == maxDepth)
				return cur;

			cur = child;
		}
		return null;
	}

	public void clear() {
		root.child00 = null;
		root.child01 = null;
		root.child10 = null;
		root.child11 = null;
		root.item = null;
		root.refs = 0;
	}

	public int size() {
		return root.refs;
	}
}
