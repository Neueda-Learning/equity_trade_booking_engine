FROM python:3.13.5-alpine

RUN addgroup -S stub && adduser -S stub -G stub
WORKDIR /app
COPY finnhub-stub.py /app/finnhub-stub.py
USER stub

EXPOSE 8080
CMD ["python3", "/app/finnhub-stub.py"]
