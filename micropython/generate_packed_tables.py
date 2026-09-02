"""Generate packed BitSquiggles mode tables for the MicroPython core."""

import os
import sys


BEGIN_MARKER = "# BEGIN GENERATED PACKED TABLES"
END_MARKER = "# END GENERATED PACKED TABLES"
TARGET = os.path.join(os.path.dirname(__file__), "bitsquiggles_core.py")

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

MARKER40 = ((2, 3, 3, 3), (3, 2, 3, 3), (3, 3, 3, 4), (3, 3, 4, 3))


def create_edges(rows, columns):
    result = []
    for row in range(rows):
        for column in range(columns):
            if column + 1 < columns:
                result.append((row, column, row, column + 1))
            if row + 1 < rows:
                result.append((row, column, row + 1, column))
    return tuple(result)


def template_definition32(edges, template_rows):
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
    for edge_index, edge in enumerate(edges):
        start_row, start_column, end_row, end_column = edge
        start = source_positions[references[start_row][start_column]]
        end = source_positions[references[end_row][end_column]]
        if start == end:
            raise ValueError("invalid BitSquiggle32 template edge")
        grouped.setdefault((min(start, end), max(start, end)), []).append(edge_index)

    classes = tuple(tuple(grouped[key]) for key in sorted(grouped))
    return classes, ()


def canonical_edge(start_row, start_column, end_row, end_column):
    if (start_row, start_column) < (end_row, end_column):
        return start_row, start_column, end_row, end_column
    return end_row, end_column, start_row, start_column


def transform40(mode_index, row, column):
    if mode_index == 0:
        return row, 6 - column
    if mode_index == 1:
        return 6 - row, column
    if mode_index == 2:
        return 6 - row, 6 - column
    return 6 - column, 6 - row


def geometric_definition40(edges, marker_indices, mode_index):
    grouped = {}
    for edge_index, edge in enumerate(edges):
        start_row, start_column, end_row, end_column = edge
        transformed_start = transform40(mode_index, start_row, start_column)
        transformed_end = transform40(mode_index, end_row, end_column)
        transformed = canonical_edge(
            transformed_start[0],
            transformed_start[1],
            transformed_end[0],
            transformed_end[1],
        )
        grouped.setdefault(min(edge, transformed), []).append(edge_index)

    classes = tuple(tuple(grouped[key]) for key in sorted(grouped))
    excluded = tuple(
        any(edge_index in marker_indices for edge_index in occurrences)
        for occurrences in classes
    )
    return classes, excluded


def pack_definition(classes, excluded):
    packed = bytearray()
    for class_index, occurrences in enumerate(classes):
        if len(occurrences) not in (1, 2):
            raise ValueError("two-byte records require one or two edges per class")
        first = occurrences[0]
        if excluded and excluded[class_index]:
            first |= 0x80
        packed.append(first)
        packed.append(occurrences[1] if len(occurrences) == 2 else 0xFF)
    return bytes(packed)


def unpack_definition(packed, has_exclusions):
    if len(packed) % 2:
        raise ValueError("packed definition must contain two-byte records")
    classes = []
    excluded = []
    for index in range(0, len(packed), 2):
        first = packed[index]
        second = packed[index + 1]
        classes.append((first & 0x7F,) if second == 0xFF else (first & 0x7F, second))
        excluded.append(bool(first & 0x80))
    return tuple(classes), tuple(excluded) if has_exclusions else ()


def definitions():
    edges32 = create_edges(7, 5)
    definitions32 = tuple(
        template_definition32(edges32, template) for template in TEMPLATES32
    )
    edges40 = create_edges(7, 7)
    marker_indices = tuple(edges40.index(edge) for edge in MARKER40)
    definitions40 = tuple(
        geometric_definition40(edges40, marker_indices, mode_index)
        for mode_index in range(4)
    )
    return definitions32, definitions40, marker_indices


def packed_tables():
    definitions32, definitions40, marker_indices = definitions()
    tables32 = tuple(
        pack_definition(classes, excluded) for classes, excluded in definitions32
    )
    tables40 = tuple(
        pack_definition(classes, excluded) for classes, excluded in definitions40
    )
    decoded32 = tuple(unpack_definition(table, False) for table in tables32)
    decoded40 = tuple(unpack_definition(table, True) for table in tables40)
    if decoded32 != definitions32 or decoded40 != definitions40:
        raise RuntimeError("packed tables do not reproduce canonical definitions")
    return tables32, tables40, bytes(marker_indices)


def bytes_literal(value):
    chunks = []
    for start in range(0, len(value), 16):
        chunks.append(
            'b"%s"' % "".join("\\x%02x" % byte for byte in value[start : start + 16])
        )
    return "\n".join("        " + chunk for chunk in chunks)


def table_tuple(name, tables):
    lines = [name + " = ("]
    for table in tables:
        lines.append("    (")
        lines.append(bytes_literal(table))
        lines.append("    ),")
    lines.append(")")
    return "\n".join(lines)


def generated_block():
    tables32, tables40, marker_indices = packed_tables()
    marker_literal = 'b"%s"' % "".join("\\x%02x" % byte for byte in marker_indices)
    return "\n".join(
        (
            BEGIN_MARKER,
            "# Generated by generate_packed_tables.py; do not edit.",
            table_tuple("_MODE_TABLES32", tables32),
            table_tuple("_MODE_TABLES40", tables40),
            "_MARKER_INDICES40 = " + marker_literal,
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
        print("generated packed tables are current")
        return
    with open(TARGET, "w", encoding="utf-8", newline="\n") as target_file:
        target_file.write(expected)
    print("updated generated packed tables: %s" % TARGET)


if __name__ == "__main__":
    main(sys.argv[1:])
