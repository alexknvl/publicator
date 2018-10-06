SOURCES := $(shell find ./src/ -name "*.scala")
TARGET  := target/pack/bin/publicator

all: $(TARGET)

$(TARGET): $(SOURCES)
	sbt pack