# AI Model Files

Place deployable CT model artifacts in this directory.

Current inference code checks these root-level files first:

- `classifier.onnx` - lesion/hemorrhage classifier used by `ct_analysis/inference/classifier.py`
- `detector.onnx` - YOLO lesion detector used by `ct_analysis/inference/detector.py`

Task-specific training outputs can be archived in subdirectories:

- `metal_severity/` - metal artifact severity classifier outputs
- `metal_binary/` - binary metal artifact classifier outputs
- `lesion_classifier/` - lesion classification outputs
- `lesion_detector/` - lesion detection outputs
- `metal_segmentation/` - metal/artifact segmentation outputs
- `lesion_segmentation/` - lesion segmentation outputs

Large model files may be stored in MinIO instead of Git. Keep the same names when
copying deployable ONNX files into the root of this directory.
