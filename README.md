# OpenMarket - API

### Abstract
This repo is maintaining the server/all API calls that support the App's features (Transaction, MarketPlace, Virtual Stamps) using services from AWS. This includes EC2, DynamoDB, Lambda, etc. All dependencies are included in build.gralde there are no additional modules required.

### How To Use It
* Clone the repository and import it into IntelliJ, then run ./gradlew build --refresh-dependencies from the home directory. This grabs all dependencies that are used in the project
* Go to ServerRunner.java and simply click run.
* You are all good to go on the server, if you wish to use our services please check out our client repo or GRPC's tutorial on how to make request to the server.


# Usage
Please check out the app repo for OpenMarket from this link
[here](https://github.com/miska12345/OpenMarket-swift)
