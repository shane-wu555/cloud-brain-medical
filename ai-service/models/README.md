# AI Model Files

Place deployable CT model artifacts in this directory.

Current inference code checks these root-level files first:

- `classifier.onnx` - lesion/hemorrhage classifier used by `ct_analysis/inference/classifier.py`
- `detector.onnx` - YOLO lesion detector used by `ct_analysis/inference/detector.py`
- `lesion_segmentation/lesion_segmentation.onnx` - lesion segmentation model
- `metal_severity/metal_classifier_severity.onnx` - metal artifact severity classifier
- `metal_segmentation/metal_segmentation.onnx` - metal/artifact segmentation model

For local development, point `ai-service/.env` at these files:

- `CT_CLASSIFIER_MODEL`
- `CT_DETECTOR_MODEL`
- `CT_LESION_SEGMENTATION_MODEL`
- `CT_LESION_SEG_THRESHOLD`
- `CT_LESION_SEG_MIN_AREA_PIXELS`
- `CT_METAL_CLASSIFIER_MODEL`
- `CT_METAL_SEGMENTATION_MODEL`

Set `CT_INFERENCE_ALLOW_MOCK=false` when validating real inference so missing
or failed model files fail the task instead of returning the demo/mock report.

Task-specific training outputs can be archived in subdirectories:

- `metal_severity/` - metal artifact severity classifier outputs
- `metal_binary/` - binary metal artifact classifier outputs
- `lesion_classifier/` - lesion classification outputs
- `lesion_detector/` - lesion detection outputs
- `metal_segmentation/` - metal/artifact segmentation outputs
- `lesion_segmentation/` - lesion segmentation outputs

Large model files may be stored in MinIO instead of Git. Keep the same names when
copying deployable ONNX files into the root of this directory.
