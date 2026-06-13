FROM python:3.10-slim

WORKDIR /app

# install deps first so they are cached when only code changes
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
RUN python -m spacy download en_core_web_lg

# copy the rest of the application code
COPY . .

EXPOSE 8081

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8081"]
