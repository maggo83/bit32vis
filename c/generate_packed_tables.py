"""Generate the packed mode tables embedded in the unified C99 core."""

import os
import sys


BEGIN_MARKER = "/* BEGIN GENERATED MODE TABLES */"
END_MARKER = "/* END GENERATED MODE TABLES */"
TARGET = os.path.join(os.path.dirname(__file__), "bitsquiggles_core.c")
MARKER = ((2, 3, 3, 3), (3, 2, 3, 3), (3, 3, 3, 4), (3, 3, 4, 3))
TEMPLATES32 = (
    (
        "A  B  C  B' A'",
        "D  E  F  E' D'",
        "G  H  I  H' G'",
        "J  K  L  K' J'",
        "M  N  O  N' M'",
        "P  Q  R  Q' P'",
        "S  T  U  T' S'",
    ),
    (
        "A  B  C  D  E",
        "F  G  H  I  J",
        "K  L  M  N  O",
        "P  Q  R  S  T",
        "K' L' M' N' O'",
        "F' G' H' I' J'",
        "A' B' C' D' E'",
    ),
    (
        "A  B  C  D  E",
        "F  G  H  I  J",
        "K  L  M  N  O",
        "P  Q  R  Q' P'",
        "O' N' M' L' K'",
        "J' I' H' G' F'",
        "E' D' C' B' A'",
    ),
    (
        "A  B  C  D  E",
        "F  G  H  I  J",
        "K  L  M  N  I'",
        "O  P  Q  M' H'",
        "R  S  P' L' G'",
        "T  R' O' K' F'",
        "E' D' C' B' A'",
    ),
)


def create_edges(rows, columns):
    result = []
    for row in range(rows):
        for column in range(columns):
            if column + 1 < columns:
                result.append((row, column, row, column + 1))
            if row + 1 < rows:
                result.append((row, column, row + 1, column))
    return tuple(result)


def definition32(edges, template_rows):
    template = tuple(tuple(row.split()) for row in template_rows)
    source_positions = {}
    references = []
    for row in range(7):
        reference_row = []
        for column in range(5):
            token = template[row][column]
            copied = token.endswith("'")
            name = token[:-1] if copied else token
            reference_row.append(name)
            if not copied:
                source_positions[name] = row * 5 + column
        references.append(tuple(reference_row))

    grouped = {}
    for index, edge in enumerate(edges):
        first = source_positions[references[edge[0]][edge[1]]]
        second = source_positions[references[edge[2]][edge[3]]]
        grouped.setdefault((min(first, second), max(first, second)), []).append(index)
    return tuple(tuple(grouped[key]) for key in sorted(grouped)), ()


def transform(mode, row, column):
    if mode == 0:
        return row, 6 - column
    if mode == 1:
        return 6 - row, column
    if mode == 2:
        return 6 - row, 6 - column
    return 6 - column, 6 - row


def canonical(start, end):
    return start + end if start < end else end + start


def definition40(edges, marker_indices, mode):
    grouped = {}
    for index, edge in enumerate(edges):
        start = transform(mode, edge[0], edge[1])
        end = transform(mode, edge[2], edge[3])
        key = min(edge, canonical(start, end))
        grouped.setdefault(key, []).append(index)
    classes = tuple(tuple(grouped[key]) for key in sorted(grouped))
    excluded = tuple(
        any(index in marker_indices for index in occurrences)
        for occurrences in classes
    )
    return classes, excluded


def pack(classes, excluded):
    result = bytearray()
    for index, occurrences in enumerate(classes):
        if len(occurrences) not in (1, 2):
            raise ValueError("packed classes require one or two edges")
        result.append(occurrences[0] | (0x80 if excluded and excluded[index] else 0))
        result.append(occurrences[1] if len(occurrences) == 2 else 0xFF)
    return bytes(result)


def tables():
    edges32 = create_edges(7, 5)
    definitions32 = tuple(
        definition32(edges32, template) for template in TEMPLATES32
    )
    edges40 = create_edges(7, 7)
    marker_indices = tuple(edges40.index(edge) for edge in MARKER)
    definitions40 = tuple(
        definition40(edges40, marker_indices, mode) for mode in range(4)
    )
    if tuple(len(classes) for classes, _ in definitions32) != (32, 31, 29, 33):
        raise RuntimeError("generated 32-bit capacities do not match the spec")
    expected_counts = ((45, 42), (45, 42), (42, 40), (42, 40))
    for value, expected in zip(definitions40, expected_counts):
        classes, excluded = value
        if (len(classes), sum(not item for item in excluded)) != expected:
            raise RuntimeError("generated 40-bit capacities do not match the spec")
    return (
        tuple(pack(*value) for value in definitions32),
        tuple(pack(*value) for value in definitions40),
        marker_indices,
    )


def array(name, values, element_type="uint8_t"):
    lines = ["static const %s %s[] = {" % (element_type, name)]
    for start in range(0, len(values), 8):
        chunk = values[start : start + 8]
        lines.append("    " + ", ".join("0x%02xu" % value for value in chunk) + ",")
    lines.append("};")
    return "\n".join(lines)


def generated_block():
    packed32, packed40, marker_indices = tables()

    def packed_arrays(width, packed):
        offsets = []
        offset = 0
        for table in packed:
            offsets.append(offset)
            offset += len(table)
        return (
            array("MODE_TABLES%s" % width, b"".join(packed)),
            array("MODE_OFFSETS%s" % width, offsets, "uint16_t"),
            array("MODE_COUNTS%s" % width, tuple(len(table) // 2 for table in packed)),
        )

    return "\n".join(
        (
            BEGIN_MARKER,
            "/* Generated by generate_packed_tables.py; do not edit. */",
            *packed_arrays(32, packed32),
            *packed_arrays(40, packed40),
            array("MARKER_INDICES", marker_indices),
            END_MARKER,
        )
    )


def replace_generated_block(source, block):
    try:
        start = source.index(BEGIN_MARKER)
        end = source.index(END_MARKER, start) + len(END_MARKER)
    except ValueError:
        raise RuntimeError("generated table markers are missing from %s" % TARGET)
    return source[:start] + block + source[end:]


def main(arguments):
    if arguments not in ([], ["--check"]):
        raise SystemExit("usage: generate_packed_tables.py [--check]")
    with open(TARGET, "r", encoding="utf-8") as target_file:
        source = target_file.read()
    expected = replace_generated_block(source, generated_block())
    if arguments == ["--check"]:
        if source != expected:
            raise SystemExit("stale generated packed tables: %s" % TARGET)
        print("generated C packed tables are current")
        return
    with open(TARGET, "w", encoding="utf-8", newline="\n") as target_file:
        target_file.write(expected)
    print("updated generated packed tables: %s" % TARGET)


if __name__ == "__main__":
    main(sys.argv[1:])