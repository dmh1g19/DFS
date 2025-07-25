.PHONY: out run clean client controller dstore

out:
	mkdir -p out
	javac -d out $(shell find . -name "*.java")

client:
	java -cp out client.ClientMain 8080 40000

controller:
	java -cp out controller.Controller 8080 3 40000 50000

dstore:
	java -cp out dstore.Dstore $(ARGS) # e.g., java -cp out dstore.Dstore 1234 8080 dstore1

clean:
	rm -rf out