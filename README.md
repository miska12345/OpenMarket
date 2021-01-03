![Development Status](https://img.shields.io/badge/Status-On%20Development-green)
# Overview
OpenMarket offers organizations a new way to create, distribute, and exchange virtual currencies through event check-ins and gift shopping, helping organizations to boost their event participation by providing an incentive for their participants. The OpenMarket platform enables organization owners to promote their organizations, attract new members, and retain the loyalty of existing members. With the OpenMarket App, users can collect organization-specific currencies by attending organization-hosted events and scanning the event QR code, which may later be used to redeem gift items in the organization's shop. Depending on the context of usage, an event is simply an opportunity for organizations to award users who participated in some activity (e.g. donating to that organization, being present at a offline event, etc). Additionally, users may follow organizations and receive push notifications when new events have been scheduled.

Although the project was created in response to the observation that student clubs often struggle to find enough participants for their events due to the lack of incentives, it is also useful for small business owners who wish to capture the benefit of such systems.

This repo contains
- Source code for OpenMarket service

# Dependencies
This project uses Gradle to manage all dependencies, please run ```./gradlew build``` to build the project.

# Components
- AccountService: handles user registeration and authentication
- OrganizationService: handles the the creation, search, and update of organization profiles
- TransactionService: handles single-currency transactions and refund
- StampEventService: handles event creation and update

# Environmental Variables
You would need to provide valid parameters for the following environmental varibales.
- TransacQueueURL: The SQS queue URL for transactions (for transaction processing).
- Port: The port number to run the service
- UseValidation: Either True or False, setting it to true would enforce cryptographic validation during authentication.
- TokenDuration: in hours, refers to the number of hours a session is considered to be valid.
- DB_URL: the jdbc URL for the MySQL database storing store items.
- DB_USER: the MySQL database username.
- DB_PASS: the MySQL database password.


# Related Repos
- [iOS Frontend](https://github.com/miska12345/OpenMarket-swift)
- [Data Access Objects (DAOs)](https://github.com/miska12345/OpenMarket-Dao)
- [GRPC proto files](https://github.com/didntpay/OpenMarketCommon)

