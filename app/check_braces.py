import sys

def check_braces(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
    
    open_count = 0
    close_count = 0
    stack = []
    
    for i, line in enumerate(lines):
        # Extremely naive ignoring strings
        for char in line:
            if char == '{':
                open_count += 1
                stack.append(i + 1)
            elif char == '}':
                close_count += 1
                if stack:
                    stack.pop()
    print("Open", open_count)
    print("Close", close_count)
    if stack:
        print("Last unclosed braces started at lines:")
        print(stack[-10:])
    else:
        print("All closed")

check_braces(sys.argv[1])
