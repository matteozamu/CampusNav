# server.py

import os
import io
import tempfile
import logging
from typing import List

import cv2
import numpy as np
from PIL import Image
from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
from fastapi.responses import JSONResponse
import ollama

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI()

# Global storage for frame analyses
frame_analyses_store = []


def extract_frames(video_data: bytes, max_frames: int = 5) -> List[bytes]:
    """Extracts frames from video bytes."""
    logger.info("Extracting frames from video data.")
    with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as temp_video:
        temp_video.write(video_data)
        temp_video_path = temp_video.name

    frames = []
    try:
        cap = cv2.VideoCapture(temp_video_path)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        logger.info(f"Total frames in video: {total_frames}")

        frame_interval = max(total_frames // max_frames, 1)
        logger.info(f"Frame interval: {frame_interval}")

        frame_count = 0
        while len(frames) < max_frames:
            ret, frame = cap.read()
            if not ret:
                logger.warning("No more frames to read.")
                break

            if frame_count % frame_interval == 0:
                frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                pil_image = Image.fromarray(frame_rgb)
                img_byte_arr = io.BytesIO()
                pil_image.save(img_byte_arr, format="JPEG")
                frames.append(img_byte_arr.getvalue())
                logger.info(f"Extracted frame {len(frames)}/{max_frames}")

            frame_count += 1

        cap.release()
    finally:
        os.unlink(temp_video_path)
        logger.info("Temporary video file deleted.")

    return frames


def analyze_frame(frame: bytes) -> str:
    """Analyze a single frame with Gemma model."""
    logger.info("Sending frame to Gemma3 for analysis.")

    prompt = (
        "#Role\n"
        "You are an assistive system that guides a blind person navigating a university campus.\n"
        "#Instructions\n"
        "Analyze the given video frame:\n"
        "- Identify obstacles\n"
        "- Describe environment transitions\n"
        "- Be concise, clear, and factual\n"
        "- No assumptions. Only observable details.\n"
    )

    response = ollama.chat(
        model="gemma3:4b",
        messages=[
            {
                "role": "user",
                "content": prompt,
                "images": [frame],
            },
        ],
    )

    return response["message"]["content"]


def summarize_video(frame_descriptions: str) -> str:
    """Generate a summary from all frames."""
    logger.info("Generating video summary.")

    summary_prompt = (
        "Based on the following frame analyses, summarize the full environment context.\n"
        "Speak clearly as if guiding a blind user.\n\n"
        f"{frame_descriptions}\n"
    )

    response = ollama.chat(
        model="gemma3:4b",
        messages=[
            {
                "role": "user",
                "content": summary_prompt,
            },
        ],
    )

    return response["message"]["content"]


@app.post("/upload_video")
async def upload_video(file: UploadFile = File(...)):
    """Upload a video for analysis."""
    logger.info(f"Received file: {file.filename}")

    if not file.content_type.startswith("video/"):
        logger.error("Uploaded file is not a video.")
        raise HTTPException(status_code=400, detail="Uploaded file must be a video.")

    video_data = await file.read()

    # Extract frames
    frames = extract_frames(video_data, max_frames=5)

    # Analyze frames
    frame_analyses = []
    frame_descriptions = ""
    for idx, frame in enumerate(frames):
        description = analyze_frame(frame)
        frame_analyses.append(f"Frame {idx + 1}: {description}")
        frame_descriptions += f"Frame {idx + 1}: {description}\n"

    # Save for future questions
    global frame_analyses_store
    frame_analyses_store = frame_analyses.copy()

    # Summarize video
    video_summary = summarize_video(frame_descriptions)

    return JSONResponse(
        content={
            "summary": video_summary,
            "frames": frame_analyses,
        }
    )


class QuestionRequest(BaseModel):
    question: str


@app.post("/ask_question")
async def ask_question(request: QuestionRequest):
    """Ask a follow-up question about the previously analyzed video."""
    if not frame_analyses_store:
        logger.error("No video analysis available yet.")
        raise HTTPException(status_code=400, detail="No video analyzed yet. Please upload a video first.")

    context = "\n".join(frame_analyses_store)

    prompt = (
        "You are an assistive AI system.\n"
        f"The user previously recorded a video and the following frames were analyzed:\n\n"
        f"{context}\n\n"
        f"Now answer the user's question clearly based only on the frame analysis:\n"
        f"Question: {request.question}\n"
    )

    response = ollama.chat(
        model="gemma3:4b",
        messages=[
            {
                "role": "user",
                "content": prompt,
            },
        ],
    )

    return JSONResponse(
        content={"answer": response["message"]["content"]}
    )

