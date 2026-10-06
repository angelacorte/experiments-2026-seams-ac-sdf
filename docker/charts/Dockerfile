FROM python:3.14.7
RUN mkdir /experiment
WORKDIR /experiment
COPY .python-version ./
COPY python python
# config.py reads the scenarios from src/main/yaml and the shapes from src/main/resources/shapes.yml
COPY src/main/yaml src/main/yaml
COPY src/main/resources/shapes.yml src/main/resources/shapes.yml
COPY requirements.txt ./
RUN pip install --upgrade pip
RUN pip install -r requirements.txt
COPY process.py ./
CMD ["python", "process.py"]