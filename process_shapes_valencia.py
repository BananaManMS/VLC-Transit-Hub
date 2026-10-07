#!/usr/bin/env python3
import json
import os
import sys

LINE_COLORS = {
    "C1": "#7AB3DE",
    "C2": "#F79529",
    "C3": "#7A2780",
    "C5": "#018A27",
    "C6": "#0D3386",
}

LINE_NAMES = {
    "40_C1": "Valencia Nord - Platja i Grau de Gandia",
    "40_C1_INV": "Platja i Grau de Gandia - Valencia Nord",
    "40_C2": "Valencia Nord - Moixent",
    "40_C2_INV": "Moixent - Valencia Nord",
    "40_C3": "Valencia Nord - Buñol",
    "40_C3_INV": "Buñol - Valencia Nord",
    "40_C5": "Puçol - Caudiel",
    "40_C5_INV": "Caudiel - Puçol",
    "40_C6": "Valencia Nord - Castelló de la Plana",
    "40_C6_INV": "Castelló de la Plana - Valencia Nord",
}

def main():
    shapes_path = "shapes.txt"
    if not os.path.exists(shapes_path):
        print(f"Error: No se encuentra {shapes_path}", file=sys.stderr)
        sys.exit(1)

    print(f"Leyendo {shapes_path}...")
    shapes_map = {}

    with open(shapes_path, "r", encoding="utf-8") as f:
        header = f.readline().strip().split(",")
        try:
            sid_idx = header.index("shape_id")
            lat_idx = header.index("shape_pt_lat")
            lon_idx = header.index("shape_pt_lon")
            seq_idx = header.index("shape_pt_sequence")
        except ValueError as e:
            print(f"Error en cabeceras de shapes.txt: {e}", file=sys.stderr)
            sys.exit(1)

        for line in f:
            line_str = line.strip()
            if not line_str:
                continue
            parts = line_str.split(",")
            if len(parts) <= max(sid_idx, lat_idx, lon_idx, seq_idx):
                continue

            shape_id = parts[sid_idx]
            # Filtrar exclusivamente núcleo 40 (Cercanías Valencia)
            if not shape_id.startswith("40_"):
                continue

            try:
                lat = float(parts[lat_idx])
                lon = float(parts[lon_idx])
                seq = int(parts[seq_idx])
            except ValueError:
                continue

            if shape_id not in shapes_map:
                shapes_map[shape_id] = {}
            shapes_map[shape_id][seq] = (lon, lat)

    print(f"Líneas de Cercanías Valencia detectadas: {len(shapes_map)}")

    features = []
    total_points = 0

    for shape_id in sorted(shapes_map.keys()):
        seq_dict = shapes_map[shape_id]
        sorted_seq = sorted(seq_dict.keys())
        # Mantener el 100% de las coordenadas originales en orden estricto de secuencia, SIN simplificación
        coords = [list(seq_dict[s]) for s in sorted_seq]
        total_points += len(coords)

        ref_code = shape_id.split("_")[1].replace("_INV", "")
        color = LINE_COLORS.get(ref_code, "#E30613")
        name = LINE_NAMES.get(shape_id, f"Línea {ref_code}")

        feature = {
            "type": "Feature",
            "properties": {
                "shape_id": shape_id,
                "ref": ref_code,
                "name": name,
                "route": "train",
                "colour": color
            },
            "geometry": {
                "type": "LineString",
                "coordinates": coords
            }
        }
        features.append(feature)
        print(f"  * {shape_id}: {len(coords)} puntos geográficos preservados")

    geojson_data = {
        "type": "FeatureCollection",
        "features": features
    }

    target_asset_path = "app/src/main/assets/ruta_cercanias_valencia.geojson"
    os.makedirs(os.path.dirname(target_asset_path), exist_ok=True)

    with open(target_asset_path, "w", encoding="utf-8") as out:
        json.dump(geojson_data, out, ensure_ascii=False, separators=(",", ":"))

    file_size_kb = os.path.getsize(target_asset_path) / 1024
    print(f"\nArchivo GeoJSON escrito directamente en: {target_asset_path}")
    print(f"Puntos totales preservados: {total_points}")
    print(f"Tamaño final: {file_size_kb:.2f} KB")

if __name__ == "__main__":
    main()
